package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getVariablesWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("variables")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariables() {

    // when
    Response response =
        embeddedOptimizeRule.target("variables")
            .queryParam("processDefinitionKey", "aKey")
            .queryParam("processDefinitionVersion", "aVersion")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
    List responseList = response.readEntity(new GenericType<List<VariableRetrievalDto>>() {
        });
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("variables/values")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariableValues() {

    // when
    Response response =
        embeddedOptimizeRule.target("variables/values")
            .queryParam("processDefinitionKey", "aKey")
            .queryParam("processDefinitionVersion", "aVersion")
            .queryParam("name", "bla")
            .queryParam("type", "Boolean")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
    List responseList = response.readEntity(new GenericType<List<String>>() {
        });
    assertThat(responseList.isEmpty(), is(true));
  }


}
