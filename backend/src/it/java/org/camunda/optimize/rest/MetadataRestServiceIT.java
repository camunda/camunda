package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.OptimizeVersionDto;
import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.metadata.MetadataService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mockito;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public class MetadataRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getOptimizeVersion() {
    // when
    Response response =
        embeddedOptimizeRule.target("meta/version")
            .request()
            .get();

    // then
    assertThat(response.getStatus(), is(200));
    OptimizeVersionDto optimizeVersionDto = response.readEntity(OptimizeVersionDto.class);
    assertThat(optimizeVersionDto.getOptimizeVersion(), is(embeddedOptimizeRule.getApplicationContext().getBean(MetadataService.class).getVersion()));
  }
}
