package com.microapp.customer.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.microapp.customer.application.CustomerEntity;
import com.microapp.customer.application.CustomerEntity.EkycRequest;
import com.microapp.customer.application.CustomersByStageView;
import com.microapp.customer.domain.Customer;
import java.util.List;

/**
 * HTTP API for the customer lifecycle. {@code id} (path) is the customer/visitor id —
 * the client mints it (visitor-&lt;uuid&gt;) and reuses it across stages. Mutating routes
 * read the entity (strongly consistent) and return an API {@link CustomerView}; only
 * the pre-login customer count reads the (eventually consistent) view.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/customers")
public class CustomerEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;

  public CustomerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record VisitorRequest(String channel) {}

  public record RegisterRequest(String email, String phone, String channel) {}

  public record KycRequest(String idType, String idNumber, boolean consent) {}

  public record OfferView(
      String code, String title, String description, String starterProductId, double bonusAmount) {}

  public record CustomerView(
      String customerId, String stage, String email, String phone, String channel,
      OfferView offer, String accountId, String kycRef) {}

  public record Tile(String id, String title, String subtitle, String icon) {}

  public record PreLoginContent(List<Tile> tiles, long customerCount) {}

  @Post("/{id}/visitor")
  public CustomerView createVisitor(String id, VisitorRequest req) {
    componentClient.forEventSourcedEntity(id).method(CustomerEntity::createVisitor).invoke(req.channel());
    return status(id);
  }

  @Post("/{id}/register")
  public CustomerView register(String id, RegisterRequest req) {
    componentClient.forEventSourcedEntity(id).method(CustomerEntity::register)
        .invoke(new Customer.Registration(req.email(), req.phone(), req.channel()));
    return status(id);
  }

  @Post("/{id}/kyc")
  public CustomerView completeKyc(String id, KycRequest req) {
    componentClient.forEventSourcedEntity(id).method(CustomerEntity::completeKyc)
        .invoke(new EkycRequest(req.idType(), req.idNumber(), req.consent()));
    return status(id);
  }

  @Get("/{id}")
  public CustomerView getCustomer(String id) {
    return status(id);
  }

  @Get("/prelogin")
  public PreLoginContent prelogin() {
    long customerCount =
        componentClient.forView().method(CustomersByStageView::byStage).invoke("CUSTOMER").customers().size();
    var tiles = List.of(
        new Tile("save", "Save smarter", "Open a CASA account in minutes", "🏦"),
        new Tile("takaful", "Protect your family", "Shariah-compliant Takaful plans", "🛡️"),
        new Tile("advisor", "AI wealth advisor", "Plan goals grounded in your real data", "💬"));
    return new PreLoginContent(tiles, customerCount);
  }

  private CustomerView status(String id) {
    var c = componentClient.forEventSourcedEntity(id).method(CustomerEntity::get).invoke();
    return toApi(c);
  }

  private static CustomerView toApi(Customer c) {
    OfferView offer = c.offer() == null ? null
        : new OfferView(c.offer().code(), c.offer().title(), c.offer().description(),
            c.offer().starterProductId(), c.offer().bonusAmount());
    return new CustomerView(
        c.customerId(), c.stage().name(), c.email(), c.phone(), c.channel(), offer, c.accountId(), c.kycRef());
  }
}
