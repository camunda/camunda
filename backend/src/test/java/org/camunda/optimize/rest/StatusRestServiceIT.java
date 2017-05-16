package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.spring.OptimizeAwareDependencyInjectionListener;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration( locations = {"/rest/restTestApplicationContext.xml"})
@TestExecutionListeners ({
    ServletTestExecutionListener.class,
    OptimizeAwareDependencyInjectionListener.class,
    DirtiesContextTestExecutionListener.class,
    TransactionalTestExecutionListener.class
})
public class StatusRestServiceIT {

  @ClassRule
  public static EmbeddedOptimizeRule embeddedOptimizeRule =
      new EmbeddedOptimizeRule("classpath*:rest/mockedEmbeddedOptimizeContext.xml");

  @Autowired
  private StatusCheckingService mockedStatusCheckingService;
  @Autowired
  private ImportProgressReporter importProgressReporter;

  @Test
  public void getConnectionStatus() {
    // given
    ConnectionStatusDto expected = new ConnectionStatusDto();
    expected.setConnectedToElasticsearch(true);
    expected.setConnectedToEngine(true);
    Mockito.when(mockedStatusCheckingService.getConnectionStatus()).thenReturn(expected);

    // when
    Response response = embeddedOptimizeRule.target("status/connection")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    ConnectionStatusDto actual =
      response.readEntity(ConnectionStatusDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.isConnectedToElasticsearch(), is(expected.isConnectedToElasticsearch()));
    assertThat(actual.isConnectedToEngine(), is(expected.isConnectedToEngine()));
  }

  @Test
  public void getImportProgressStatus() throws OptimizeException {
    // given
    int expectedCount = 80;
    Mockito.when(importProgressReporter.computeImportProgress()).thenReturn(expectedCount);

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
    Mockito.when(importProgressReporter.computeImportProgress()).thenThrow(new OptimizeException(errorMessage));

    // when
    Response response = embeddedOptimizeRule.target("status/import-progress")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("It was not possible to compute the import progress"), is(true));
  }
}
