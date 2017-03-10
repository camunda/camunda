package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.ConnectionStatusDto;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class StatusRestServiceTest extends AbstractJerseyTest{

  @Autowired
  private StatusCheckingService mockedStatusCheckingService;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getConnectionStatus() {
    // given
    ConnectionStatusDto expected = new ConnectionStatusDto();
    expected.setConnectedToElasticsearch(true);
    expected.setConnectedToEngine(true);
    Mockito.when(mockedStatusCheckingService.getConnectionStatus()).thenReturn(expected);

    // when
    Response response =
      target("status/connection")
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

}
