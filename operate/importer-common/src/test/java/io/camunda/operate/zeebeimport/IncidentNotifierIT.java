/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_ALERTS;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_BPMN_PROCESS_ID;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_CREATION_TIME;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_ERROR_MESSAGE;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_ERROR_TYPE;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_FLOW_NODE_ID;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_JOB_KEY;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_MESSAGE;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_PROCESS_INSTANCE_ID;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_PROCESS_KEY;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_PROCESS_NAME;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_PROCESS_VERSION;
import static io.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_STATE;
import static io.camunda.operate.zeebeimport.IncidentNotifier.MESSAGE;
import static io.camunda.webapps.schema.entities.incident.ErrorType.JOB_NO_RETRIES;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebeimport.util.TestApplicationWithNoBeans;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IncidentNotifier.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class
    },
    properties = {"camunda.operate.alert.webhook=" + IncidentNotifierIT.ALERT_WEBHOOKURL_URL})
public class IncidentNotifierIT {

  protected static final String ALERT_WEBHOOKURL_URL = "http://WEBHOOKURL/path";
  private final String m2mToken = "mockM2mToken";
  private final String incident1Id = "incident1";
  private final String incident2Id = "incident2";
  private final Long processInstanceKey = 123L;
  private final String errorMessage = "errorMessage";
  private final ErrorType errorType = JOB_NO_RETRIES;
  private final String flowNodeId = "flowNodeId1";
  private final Long flowNodeInstanceId = 234L;
  private final Long processDefinitionKey = 345L;
  private final Long jobKey = 456L;
  private final IncidentState incidentState = IncidentState.ACTIVE;
  private final String bpmnProcessId = "testProcessId";
  private final String processName = "processName";
  private final int processVersion = 234;
  @MockBean private M2mTokenManager m2mTokenManager;
  @MockBean private ProcessCache processCache;

  @MockBean
  @Qualifier("incidentNotificationRestTemplate")
  private RestTemplate restTemplate;

  @Autowired @InjectMocks private IncidentNotifier incidentNotifier;

  @Before
  public void setup() {
    when(processCache.findOrWaitProcess(any(), anyInt(), anyLong()))
        .thenReturn(
            Optional.of(
                new ProcessEntity()
                    .setId("123")
                    .setBpmnProcessId(bpmnProcessId)
                    .setName(processName)
                    .setVersion(processVersion)));
  }

  @Test
  public void testIncidentsNotificationIsSent() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .willReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

    // when
    final List<IncidentEntity> incidents =
        asList(createIncident(incident1Id), createIncident(incident2Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // then
    final ArgumentCaptor<HttpEntity<String>> requestCaptor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate, times(1))
        .postForEntity(eq(ALERT_WEBHOOKURL_URL), requestCaptor.capture(), eq(String.class));
    final HttpEntity<String> request = requestCaptor.getValue();

    assertThat(request.getHeaders().get("Authorization").get(0)).isEqualTo("Bearer " + m2mToken);
    final String body = request.getBody();

    // assert body
    final DocumentContext jsonContext = JsonPath.parse(body);
    final String alerts = "$." + FIELD_NAME_ALERTS;
    assertThat(jsonContext.read(alerts, Object.class)).isNotNull();
    assertThat(jsonContext.read(alerts + ".length()", Integer.class)).isEqualTo(2);
    assertThat(jsonContext.read(alerts + "[0].id", String.class)).isEqualTo(incident1Id);
    assertIncidentFields(jsonContext.read(alerts + "[0]", HashMap.class));
    assertThat(jsonContext.read(alerts + "[1].id", String.class)).isEqualTo(incident2Id);
    assertIncidentFields(jsonContext.read(alerts + "[1]", HashMap.class));
  }

  @Test
  public void testTokenIsNotValid() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(m2mTokenManager.getToken(anyBoolean())).willReturn(m2mToken);
    // the first call will return UNAUTHORIZED, the second - OK
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(
            new ResponseEntity<>(HttpStatus.UNAUTHORIZED), new ResponseEntity<>(HttpStatus.OK));

    // when
    final List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // then
    // new token was requested
    verify(m2mTokenManager, times(1)).getToken(eq(true));
    // incident data was sent
    final ArgumentCaptor<HttpEntity<String>> requestCaptor =
        ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate, times(2))
        .postForEntity(eq(ALERT_WEBHOOKURL_URL), requestCaptor.capture(), eq(String.class));
    final HttpEntity<String> request = requestCaptor.getValue();
    assertThat(request.getHeaders().get("Authorization").get(0)).isEqualTo("Bearer " + m2mToken);
    final String body = request.getBody();
    final DocumentContext jsonContext = JsonPath.parse(body);
    final String alerts = "$." + FIELD_NAME_ALERTS;
    assertThat(jsonContext.read(alerts, Object.class)).isNotNull();
    assertThat(jsonContext.read(alerts + ".length()", Integer.class)).isEqualTo(1);
    assertThat(jsonContext.read(alerts + "[0].id", String.class)).isEqualTo(incident1Id);
    assertIncidentFields(jsonContext.read(alerts + "[0]", HashMap.class));
  }

  @Test
  public void testNotificationFailed() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    // webhook returns status 500
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    // when
    final List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // silently fails without exception
  }

  @Test
  public void testNotificationFailedFromSecondAttempt() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(m2mTokenManager.getToken(anyBoolean())).willReturn(m2mToken);
    // the first call will return UNAUTHORIZED, the second - 500
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(
            new ResponseEntity<>(HttpStatus.UNAUTHORIZED),
            new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    // when
    final List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // then
    // new token was requested
    verify(m2mTokenManager, times(1)).getToken(eq(true));
    // silently fails without exception
  }

  @Test
  public void testNotificationFailedWithException() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    // notification will throw exception
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new RuntimeException("Something went wrong"));

    // when
    final List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // then
    // silently fails without exception
  }

  private void assertIncidentFields(final HashMap incidentFields) {
    assertThat(incidentFields.get(FIELD_NAME_MESSAGE)).isEqualTo(MESSAGE);
    assertThat(incidentFields.get(FIELD_NAME_JOB_KEY)).isEqualTo(jobKey.intValue());
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_KEY))
        .isEqualTo(processDefinitionKey.intValue());
    assertThat(incidentFields.get(FIELD_NAME_BPMN_PROCESS_ID)).isEqualTo(bpmnProcessId);
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_NAME)).isEqualTo(processName);
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_VERSION)).isEqualTo(processVersion);
    assertThat(incidentFields.get(FIELD_NAME_FLOW_NODE_INSTANCE_KEY))
        .isEqualTo(flowNodeInstanceId.intValue());
    assertThat(incidentFields.get(FIELD_NAME_CREATION_TIME)).isNotNull();
    assertThat(incidentFields.get(FIELD_NAME_ERROR_MESSAGE)).isEqualTo(errorMessage);
    assertThat(incidentFields.get(FIELD_NAME_ERROR_TYPE)).isEqualTo(errorType.name());
    assertThat(incidentFields.get(FIELD_NAME_FLOW_NODE_ID)).isEqualTo(flowNodeId);
    assertThat(incidentFields.get(FIELD_NAME_STATE)).isEqualTo(incidentState.name());
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_INSTANCE_ID))
        .isEqualTo(String.valueOf(processInstanceKey));
  }

  private IncidentEntity createIncident(final String id) {
    return new IncidentEntity()
        .setId(id)
        .setCreationTime(OffsetDateTime.now())
        .setProcessInstanceKey(processInstanceKey)
        .setErrorMessage(errorMessage)
        .setErrorType(errorType)
        .setFlowNodeId(flowNodeId)
        .setFlowNodeInstanceKey(flowNodeInstanceId)
        .setProcessDefinitionKey(processDefinitionKey)
        .setBpmnProcessId(bpmnProcessId)
        .setJobKey(jobKey)
        .setState(incidentState);
  }
}
