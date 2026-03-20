# Event Sourcing and CQRS Implementation Details

This document dives into the implementation details of Event Sourcing and CQRS in this codebase. The custom `akka.javasdk` framework provides a high-level abstraction over Akka Persistence and Akka Projections, which are the underlying technologies that power these patterns.

## Event Sourcing Implementation

Event Sourcing is implemented via **Event Sourced Entities**. These are persistent actors that, instead of storing their current state, store a sequence of events that have happened to them. The current state is derived by replaying these events.

Let's look at `ProductEntity.java` as a prime example.

### 1. The Entity

The `ProductEntity` class extends `EventSourcedEntity<Product, ProductEvent>`.

-   `Product`: This is the **State** of the entity. It's an immutable record representing the current state of a product.
-   `ProductEvent`: This is the supertype for all **Events** that can occur to a product (e.g., `ProductCreated`, `ProductUpdated`).

```java
// backend/product-service/src/main/java/com/microapp/product/application/ProductEntity.java

@Component(id = "product")
public class ProductEntity extends EventSourcedEntity<Product, ProductEvent> {
    // ...
}
```

### 2. Commands

Commands are methods in the entity that represent an intent to change the state. In the `ProductEndpoint`, the call `componentClient.forEventSourcedEntity(productId).method(ProductEntity::create).invoke(product)` sends a `create` command to the `ProductEntity`.

The `create` method in `ProductEntity` handles this command:

```java
// backend/product-service/src/main/java/com/microapp/product/application/ProductEntity.java

public Effect<Done> create(Product product) {
    if (isActive()) {
      // Already exists — idempotent
      return effects().reply(done());
    }
    logger.info("Creating product '{}'", product.productId());
    return effects()
        .persist(new ProductCreated(/*...product details...*/)) // 1. Persist the event
        .thenReply(__ -> done());                             // 2. Reply to the caller
}
```

This method doesn't change the state directly. Instead, it validates the command and, if successful, creates and persists a `ProductCreated` event.

### 3. Events

Events are immutable records that represent a fact that has already happened.

```java
// A simplified representation of what's in the domain package
public sealed interface ProductEvent {
    record ProductCreated(/*...*/) implements ProductEvent {}
    record ProductUpdated(/*...*/) implements ProductEvent {}
    record ProductDeleted() implements ProductEvent {}
}
```

### 4. Applying Events (State Derivation)

When an event is persisted, the `applyEvent` method is called. This is where the state is actually updated. The entity takes its current state, applies the event, and returns the *new* state.

```java
// backend/product-service/src/main/java/com/microapp/product/application/ProductEntity.java

@Override
public Product applyEvent(ProductEvent event) {
    return switch (event) {
      case ProductCreated created -> new Product(
          created.productId(),
          created.productName(),
          // ...
      );
      case ProductUpdated updated -> new Product(
          currentState().productId(), // Use existing ID
          updated.productName(),      // Use updated name
          // ...
      );
      // ...
    };
}
```

When the entity needs to be recovered (e.g., after a service restart), the framework automatically replays all the persisted events for that entity ID through the `applyEvent` method to reconstruct its latest state.

## CQRS Implementation

CQRS (Command Query Responsibility Segregation) is the principle of separating the model used for writing data (the "command" side) from the model used for reading data (the "query" side).

In this project:

-   **Command Side:** The `ProductEntity` and `StatementEntity` are the command-side models. They are optimized for transactional consistency and enforcing business rules.
-   **Query Side:** The `StatementsByAccountView` is a query-side model (also called a "Projection" or "Read Model"). It's a denormalized view of the data optimized for a specific query.

Let's look at `StatementsByAccountView.java`.

### 1. The View

The view is responsible for consuming events from one or more event-sourced entities and building a queryable table.

```java
// backend/statement-service/src/main/java/com/microapp/statement/application/StatementsByAccountView.java

@Component(id = "statements-by-account")
public class StatementsByAccountView extends View {
    // ...
}
```

### 2. Consuming Events

The `StatementSummaryUpdater` is an inner class that defines how to process events. The `@Consume.FromEventSourcedEntity(StatementEntity.class)` annotation tells the framework to subscribe to the event stream from `StatementEntity`.

The `onEvent` method handles each event and updates a row in the view's table.

```java
// backend/statement-service/src/main/java/com/microapp/statement/application/StatementsByAccountView.java

@Consume.FromEventSourcedEntity(StatementEntity.class)
public static class StatementSummaryUpdater extends TableUpdater<StatementSummary> {

    public Effect<StatementSummary> onEvent(StatementEvent event) {
      return switch (event) {
        case StatementCreated created -> {
          // ...
          yield effects().updateRow(new StatementSummary(/*...*/)); // Creates a new row
        }
        case TransactionAdded added -> {
          var current = rowState(); // Gets the current row state
          // ...
          yield effects().updateRow(new StatementSummary(/*...*/)); // Updates the existing row
        }
      };
    }
}
```

This updater listens for `StatementCreated` and `TransactionAdded` events. When it receives one, it either creates or updates a `StatementSummary` record, which is a flattened, denormalized version of the data perfect for display in a list.

### 3. The Query

The view then exposes a query method that can be called to retrieve the data. The `@Query` annotation defines the query against the view's internal table.

```java
// backend/statement-service/src/main/java/com/microapp/statement/application/StatementsByAccountView.java

@Query("SELECT * AS statements FROM statements_by_account WHERE accountId = :accountId")
public QueryEffect<StatementSummaries> getByAccount(String accountId) {
    return queryResult();
}
```

This is called from the `StatementEndpoint` like this:

```java
// backend/statement-service/src/main/java/com/microapp/statement/api/StatementEndpoint.java

public Collection<StatementSummary> getStatements(String accountId) {
    return componentClient
        .forView() // Use the query side
        .method(StatementsByAccountView::getByAccount)
        .invoke(accountId)
        .statements();
}
```

## Summary Flow

Here’s the end-to-end flow for adding a transaction (`POST /accounts/{id}/statements/{sid}/transactions`):

1.  **Endpoint:** `StatementEndpoint` receives the request.
2.  **Command:** It uses `componentClient.forEventSourcedEntity(...)` to send an `addTransaction` command to the correct `StatementEntity`.
3.  **Event Persistence:** The `StatementEntity` validates the command and persists a `TransactionAdded` event to its durable event log (e.g., a database like Cassandra or PostgreSQL).
4.  **State Update:** The `StatementEntity` calls its own `applyEvent` method with the `TransactionAdded` event to update its internal state (e.g., add the transaction to its list and update the total debits).
5.  **Projection (CQRS):** Asynchronously, the `StatementsByAccountView`'s `StatementSummaryUpdater` receives the `TransactionAdded` event.
6.  **View Update:** The updater loads the current `StatementSummary` for that statement, updates the transaction count and total debits, and saves the new version of the summary to its own table.
7.  **Query:** When a user later requests the list of statements (`GET /accounts/{id}/statements`), the `StatementEndpoint` queries the `StatementsByAccountView`, which efficiently returns the pre-computed, up-to-date summary without having to re-calculate anything from the event log.
