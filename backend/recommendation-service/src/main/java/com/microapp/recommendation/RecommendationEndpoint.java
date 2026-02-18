package com.microapp.recommendation;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.HttpResponses;
import akka.http.javadsl.model.HttpResponse;

import com.microapp.recommendation.model.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/accounts")
public class RecommendationEndpoint extends AbstractHttpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEndpoint.class);

    private final HttpClient analysisClient;

    public RecommendationEndpoint(HttpClientProvider httpClientProvider) {
        this.analysisClient = httpClientProvider.httpClientFor("analysis-service");
    }

    @Get("/{accountId}/recommendations")
    public HttpResponse getRecommendations(String accountId) {
        String statementId = requestContext().queryParams().getString("statementId").orElse("stmt-2025-12");

        log.info("Fetching analysis summary from analysis-service for {}/{}", accountId, statementId);
        var analysisResponse = analysisClient
            .GET("/accounts/" + accountId + "/analysis/summary?statementId=" + statementId)
            .responseBodyAs(String.class)
            .invoke();

        List<Recommendation> recommendations = RecommendationEngine.generateRecommendations(
            accountId, statementId, analysisResponse.body());
        return HttpResponses.ok(recommendations);
    }
}
