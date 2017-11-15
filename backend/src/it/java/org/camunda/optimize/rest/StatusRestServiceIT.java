package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.InvalidTokenException;
import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
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
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class StatusRestServiceIT {

  public static final String ENGINE_ALIAS = "1";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private Client mockedEngineClient;
  private ConfigurationService configurationService;

  @Before
  public void initClients() throws InvalidTokenException {
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  private void mockEngineClient() {
    mockedEngineClient = Mockito.mock(Client.class);
    StatusCheckingService statusCheckingService =
      embeddedOptimizeRule.getApplicationContext().getBean(StatusCheckingService.class);
    statusCheckingService.setEngineClient(mockedEngineClient);
  }

  @After
  public void resetMocks() throws InvalidTokenException {
    if (mockedEngineClient != null) {
      Mockito.reset(mockedEngineClient);
    }
    StatusCheckingService statusCheckingService =
      embeddedOptimizeRule.getApplicationContext().getBean(StatusCheckingService.class);
    Client engineClient = embeddedOptimizeRule.getApplicationContext().getBean(Client.class);
    statusCheckingService.setEngineClient(engineClient);
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
    embeddedOptimizeRule.target("status/import-progress")
        .request()
        .get();
    //then
    Mockito.verify(tokenService, Mockito.times(0)).validateToken(Mockito.anyString());
  }

  @Test
  public void getConnectionStatusOk() throws Exception {
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
  public void getConnectionStatusForMissingEngineConnection() throws Exception {
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
  public void getImportProgressStatus() throws Exception {
    // given
    long expectedCount = 0L;

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
  public void getImportProgressThrowsErrorIfNoConnectionAvailable() throws Exception {
    // given
    mockEngineClient();
    String errorMessage = "Error";
    Mockito.when(mockedEngineClient.target(Mockito.anyString())).thenThrow(new RuntimeException(errorMessage));
    List<String> processDefinitionIdsToImport = new ArrayList<>();
    processDefinitionIdsToImport.add("test");
    configurationService.setProcessDefinitionIdsToImport(processDefinitionIdsToImport);

    // when
    Response response = embeddedOptimizeRule.target("status/import-progress")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("It was not possible to compute the import progress"), is(true));
  }
}
