package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.TokenService;
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


public class StatusRestServiceIT {

  public static final String ENGINE_ALIAS = "1";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private Client mockedEngineClient;

  private void mockEngineClient() {
    mockedEngineClient = Mockito.mock(Client.class);
    EngineContextFactory mockedFactory = Mockito.mock(EngineContextFactory.class);
    List<EngineContext> list = new ArrayList<>();
    EngineContext context = new EngineContext();
    context.setEngineAlias(ENGINE_ALIAS);
    context.setEngineClient(mockedEngineClient);
    list.add(context);
    Mockito.when(mockedFactory.getConfiguredEngines()).thenReturn(list);

    StatusCheckingService statusCheckingService =
      embeddedOptimizeRule.getApplicationContext().getBean(StatusCheckingService.class);

    statusCheckingService.setEngineContextFactory(mockedFactory);
  }

  @After
  public void resetMocks() {
    if (mockedEngineClient != null) {
      Mockito.reset(mockedEngineClient);
    }

    StatusCheckingService statusCheckingService =
        embeddedOptimizeRule.getApplicationContext().getBean(StatusCheckingService.class);

    EngineContextFactory realFactory = embeddedOptimizeRule.getApplicationContext().getBean(EngineContextFactory.class);
    statusCheckingService.setEngineContextFactory(realFactory);
  }

  @Test
  public void verifySecurityBypass() throws Exception {
    TokenService tokenService = embeddedOptimizeRule.getApplicationContext().getBean(TokenService.class);
    //in case this is not the first test to use the spy
    Mockito.reset(tokenService);
    // when
    embeddedOptimizeRule.target("status/connection")
        .request()
        .get();
    //then
    Mockito.verify(tokenService, Mockito.times(0)).validateToken(Mockito.anyString());

    // when
    embeddedOptimizeRule.target("status")
        .request()
        .get();
    //then
    Mockito.verify(tokenService, Mockito.times(0)).validateToken(Mockito.anyString());
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
    assertThat(actual.getEngineConnections(), is(notNullValue()));
    assertThat(actual.getEngineConnections().get(ENGINE_ALIAS), is(true));
  }

  @Test
  public void getConnectionStatusForMissingEngineConnection() {
    // given
    mockEngineClient();
    String errorMessage = "Error";
    Mockito.when(
        mockedEngineClient.target(Mockito.anyString())
    ).thenThrow(
        new RuntimeException(errorMessage)
    );

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
    assertThat(actual.getEngineConnections(), is(notNullValue()));
    assertThat(actual.getEngineConnections().get(ENGINE_ALIAS), is(false));
  }

  @Test
  public void getImportStatus() {
    // when
    Response response = embeddedOptimizeRule.target("status")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    StatusWithProgressDto actual =
      response.readEntity(StatusWithProgressDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getConnectionStatus().isConnectedToElasticsearch(), is(true));
    assertThat(actual.getConnectionStatus().getEngineConnections(), is(notNullValue()));
    assertThat(actual.getConnectionStatus().getEngineConnections().get(ENGINE_ALIAS), is(true));
    assertThat(actual.getIsImporting().get(ENGINE_ALIAS), is(false));
  }
  
}
