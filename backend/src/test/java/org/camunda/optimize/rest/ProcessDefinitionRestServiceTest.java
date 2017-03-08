package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.CorrelationQueryDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.dto.optimize.GatewaySplitDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.CorrelationReader;
import org.camunda.optimize.service.es.reader.DurationHeatMapReader;
import org.camunda.optimize.service.es.reader.FrequencyHeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.security.AuthenticationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ProcessDefinitionRestServiceTest extends AbstractJerseyTest {

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @Autowired
  private FrequencyHeatMapReader frequencyHeatMapReader;

  @Autowired
  private DurationHeatMapReader durationHeatMapReader;

  @Autowired
  private CorrelationReader correlationReader;

  @InjectMocks
  @Autowired
  private AuthenticationService authenticationService;

  @Mock
  private AuthenticationProvider engineAuthenticationProvider;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(engineAuthenticationProvider.authenticate(Mockito.any())).thenReturn(true);
  }

  @Test
  public void getProcessDefinitionsWithoutAuthentication() throws IOException {
    // when
    Response response =
      target("process-definition")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitions() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = "123";
    expected.setId(expectedProcessDefinitionId);
    Mockito.when(processDefinitionReader.getProcessDefinitions()).thenReturn(Collections.singletonList(expected));

    // when
    Response response =
      target("process-definition")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ProcessDefinitionEngineDto> definitions =
      response.readEntity(new GenericType<List<ProcessDefinitionEngineDto>>(){});
    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
  }

  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() throws IOException {
    // when
    Response response =
      target("process-definition/123/xml")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionXml() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    String expectedXml = "ProcessModelXml";
    Mockito.when(processDefinitionReader.getProcessDefinitionXml("123")).thenReturn(expectedXml);

    // when
    Response response =
      target("process-definition/123/xml")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String actualXml =
      response.readEntity(String.class);
    assertThat(actualXml, is(expectedXml));
  }

  @Test
  public void getFrequencyHeatMapWithoutAuthentication() throws IOException {
    // when
    Response response =
      target("process-definition/123/heatmap/frequency")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatMap() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(frequencyHeatMapReader.getHeatMap(Mockito.anyString())).thenReturn(expected);

    // when
    Response response =
      target("process-definition/123/heatmap/frequency")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
      response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(expected.getPiCount()));
  }

  @Test
  public void getFrequencyHeatMapPostWithoutAuthentication() throws IOException {
    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      target("process-definition/heatmap/frequency")
      .request()
      .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatPostMap() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(frequencyHeatMapReader.getHeatMap(Mockito.any(HeatMapQueryDto.class))).thenReturn(expected);

    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      target("process-definition/heatmap/frequency")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
      response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(expected.getPiCount()));
  }

  @Test
  public void getDurationHeatMapWithoutAuthentication() throws IOException {
    // when
    Response response =
      target("process-definition/123/heatmap/duration")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDurationHeatMap() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(durationHeatMapReader.getHeatMap(Mockito.anyString())).thenReturn(expected);

    // when
    Response response =
      target("process-definition/123/heatmap/duration")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
      response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(expected.getPiCount()));
  }

  @Test
  public void getDurationHeatMapPostWithoutAuthentication() throws IOException {
    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      target("process-definition/heatmap/duration")
      .request()
      .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDurationHeatMapAsPost() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(durationHeatMapReader.getHeatMap(Mockito.any(HeatMapQueryDto.class))).thenReturn(expected);

    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      target("process-definition/heatmap/duration")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
      response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(expected.getPiCount()));
  }

  @Test
  public void getCorrelationWithoutAuthentication() throws IOException {
    // when
    Entity<CorrelationQueryDto> entity = Entity.entity(new CorrelationQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      target("process-definition/correlation")
      .request()
      .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCorrelation() throws IOException {
    // given some mocks
    String token = authenticateAdmin();
    GatewaySplitDto expected = new GatewaySplitDto();
    expected.setTotal(10L);
    Mockito.when(correlationReader.activityCorrelation(Mockito.any(CorrelationQueryDto.class))).thenReturn(expected);

    // when
    Entity<CorrelationQueryDto> entity = Entity.entity(new CorrelationQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      target("process-definition/correlation")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    GatewaySplitDto actual =
      response.readEntity(GatewaySplitDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getTotal(), is(expected.getTotal()));
  }

  private String authenticateAdmin() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("admin");
    entity.setPassword("admin");

    Response tokenResponse =  target("authentication")
        .request()
        .post(Entity.json(entity));

    return tokenResponse.readEntity(String.class);
  }

}
