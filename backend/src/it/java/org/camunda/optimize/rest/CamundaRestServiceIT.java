package org.camunda.optimize.rest;

import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CamundaRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Test
  public void getCamundaWebappsEndpointWithoutAuthorization() {
    // when
    Response response =
        embeddedOptimizeRule.target("camunda")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDefaultCamundaWebappsEndpoint() {
    // when
    Response response =
        embeddedOptimizeRule.target("camunda")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
    String webappsEndpoint = response.readEntity(String.class);
    assertThat(webappsEndpoint, is("http://localhost:8080/camunda/"));
  }

  @Test
  public void getCustomCamundaWebappsEndpoint() {
    // given
    embeddedOptimizeRule.getConfigurationService().setCamundaWebappsEndpoint("foo");

    // when
    Response response =
        embeddedOptimizeRule.target("camunda")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then
    assertThat(response.getStatus(), is(200));
    String webappsEndpoint = response.readEntity(String.class);
    assertThat(webappsEndpoint, is("foo"));
  }

  @Test
  public void disableWebappsEndpoint() {
    // given
    embeddedOptimizeRule.getConfigurationService().setCamundaWebappsEndpointEnabled(false);

    // when
    Response response =
        embeddedOptimizeRule.target("camunda")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then
    assertThat(response.getStatus(), is(204));
  }
}