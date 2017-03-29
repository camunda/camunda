package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.CorrelationQueryDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.GatewaySplitDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.service.es.reader.CorrelationReader;
import org.camunda.optimize.service.es.reader.DurationHeatMapReader;
import org.camunda.optimize.service.es.reader.FrequencyHeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.security.AuthenticationService;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.spring.OptimizeAwareDependencyInjectionListener;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;

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
@ContextConfiguration(locations = { "/restTestApplicationContext.xml" })
@TestExecutionListeners({
    ServletTestExecutionListener.class,
    OptimizeAwareDependencyInjectionListener.class,
    DirtiesContextTestExecutionListener.class,
    TransactionalTestExecutionListener.class
})
public class ProcessDefinitionRestServiceTest {

  @ClassRule
  public static EmbeddedOptimizeRule embeddedOptimizeRule =
      new EmbeddedOptimizeRule("classpath*:mockedEmbeddedOptimizeContext.xml");

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
      embeddedOptimizeRule.target("process-definition")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitions() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    ExtendedProcessDefinitionOptimizeDto expected = new ExtendedProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = "123";
    expected.setId(expectedProcessDefinitionId);
    Mockito.when(processDefinitionReader.getProcessDefinitions(Mockito.eq(false))).thenReturn(Collections.singletonList(expected));

    // when
    Response response =
      embeddedOptimizeRule.target("process-definition")
      .request()
      .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
      response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
  }

  @Test
  public void getProcessDefinitionsWithXml() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    ExtendedProcessDefinitionOptimizeDto expected = new ExtendedProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = "123";
    expected.setId(expectedProcessDefinitionId);
    expected.setBpmn20Xml("test");
    Mockito.when(processDefinitionReader.getProcessDefinitions(Mockito.eq(true))).thenReturn(Collections.singletonList(expected));

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .queryParam("includeXml", true)
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
    assertThat(definitions.get(0).getBpmn20Xml(), is("test"));
  }


  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("process-definition/123/xml")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionXml() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    String expectedXml = "ProcessModelXml";
    Mockito.when(processDefinitionReader.getProcessDefinitionXml("123")).thenReturn(expectedXml);

    // when
    Response response =
      embeddedOptimizeRule.target("process-definition/123/xml")
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
      embeddedOptimizeRule.target("process-definition/123/heatmap/frequency")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatMap() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(frequencyHeatMapReader.getHeatMap(Mockito.anyString())).thenReturn(expected);

    // when
    Response response =
      embeddedOptimizeRule.target("process-definition/123/heatmap/frequency")
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
      embeddedOptimizeRule.target("process-definition/heatmap/frequency")
      .request()
      .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatPostMap() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(frequencyHeatMapReader.getHeatMap(Mockito.any(HeatMapQueryDto.class))).thenReturn(expected);

    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      embeddedOptimizeRule.target("process-definition/heatmap/frequency")
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
      embeddedOptimizeRule.target("process-definition/123/heatmap/duration")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDurationHeatMap() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(durationHeatMapReader.getHeatMap(Mockito.anyString())).thenReturn(expected);

    // when
    Response response =
      embeddedOptimizeRule.target("process-definition/123/heatmap/duration")
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
      embeddedOptimizeRule.target("process-definition/heatmap/duration")
      .request()
      .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDurationHeatMapAsPost() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto expected = new HeatMapResponseDto();
    expected.setPiCount(10L);
    Mockito.when(durationHeatMapReader.getHeatMap(Mockito.any(HeatMapQueryDto.class))).thenReturn(expected);

    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      embeddedOptimizeRule.target("process-definition/heatmap/duration")
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
      embeddedOptimizeRule.target("process-definition/correlation")
      .request()
      .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCorrelation() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    GatewaySplitDto expected = new GatewaySplitDto();
    expected.setTotal(10L);
    Mockito.when(correlationReader.activityCorrelation(Mockito.any(CorrelationQueryDto.class))).thenReturn(expected);

    // when
    Entity<CorrelationQueryDto> entity = Entity.entity(new CorrelationQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
      embeddedOptimizeRule.target("process-definition/correlation")
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

}
