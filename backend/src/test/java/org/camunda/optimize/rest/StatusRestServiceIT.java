package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class StatusRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private Client engineClient;

  @Before
  public void initClients() {
    engineClient = embeddedOptimizeRule.getApplicationContext().getBean(Client.class);
  }

  @After
  public void resetMocks() {
    Mockito.reset(engineClient);
  }

  @Test
  public void getConnectionStatusOk() {
    // when
    Response response = embeddedOptimizeRule.target("status/connection")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    ConnectionStatusDto actual =
      response.readEntity(ConnectionStatusDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.isConnectedToElasticsearch(), is(true));
    assertThat(actual.isConnectedToEngine(), is(true));
  }

  @Test
  public void getConnectionStatusForMissingEngineConnection() throws IOException {
    // given
    String errorMessage = "Error";
    Mockito.when(engineClient.target(Mockito.anyString())).thenThrow(new RuntimeException(errorMessage));

    // when
    Response response = embeddedOptimizeRule.target("status/connection")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    ConnectionStatusDto actual =
      response.readEntity(ConnectionStatusDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.isConnectedToElasticsearch(), is(true));
    assertThat(actual.isConnectedToEngine(), is(false));
  }

  @Test
  public void getImportProgressStatus() throws OptimizeException {
    // given
    int expectedCount = 0;

    // when
    Response response = embeddedOptimizeRule.target("status/import-progress")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    ProgressDto actual =
      response.readEntity(ProgressDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getProgress(), is(expectedCount));
  }

  @Test
  public void getImportProgressThrowsErrorIfNoConnectionAvailable() throws OptimizeException {
    // given
    String errorMessage = "Error";
    Mockito.when(engineClient.target(Mockito.anyString())).thenThrow(new RuntimeException(errorMessage));

    // when
    Response response = embeddedOptimizeRule.target("status/import-progress")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("It was not possible to compute the import progress"), is(true));
  }
}
