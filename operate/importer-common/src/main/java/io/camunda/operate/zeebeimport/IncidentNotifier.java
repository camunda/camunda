/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Class that sends notifications about the created incidents on configured URL. */
@Component
public class IncidentNotifier {

  protected static final String FIELD_NAME_ALERTS = "alerts";
  protected static final String FIELD_NAME_MESSAGE = "message";
  protected static final String MESSAGE = "Incident created";
  protected static final String FIELD_NAME_ID = "id";
  protected static final String FIELD_NAME_PROCESS_INSTANCE_ID = "processInstanceId";
  protected static final String FIELD_NAME_CREATION_TIME = "creationTime";
  protected static final String FIELD_NAME_STATE = "state";
  protected static final String FIELD_NAME_ERROR_MESSAGE = "errorMessage";
  protected static final String FIELD_NAME_ERROR_TYPE = "errorType";
  protected static final String FIELD_NAME_FLOW_NODE_ID = "flowNodeId";
  protected static final String FIELD_NAME_FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  protected static final String FIELD_NAME_JOB_KEY = "jobKey";
  protected static final String FIELD_NAME_PROCESS_KEY = "processDefinitionKey";
  protected static final String FIELD_NAME_BPMN_PROCESS_ID = "bpmnProcessId";
  protected static final String FIELD_NAME_PROCESS_NAME = "processName";
  protected static final String FIELD_NAME_PROCESS_VERSION = "processVersion";
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentNotifier.class);
  @Autowired private OperateProperties operateProperties;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private M2mTokenManager m2mTokenManager;

  @Autowired
  @Qualifier("incidentNotificationRestTemplate")
  private RestTemplate restTemplate;

  @Autowired private ProcessCache processCache;

  public void notifyOnIncidents(final List<IncidentEntity> incidents) {
    try {
      HttpStatusCode status = notifyOnIncidents(incidents, m2mTokenManager.getToken());

      if (status.is2xxSuccessful()) {
        LOGGER.debug("Incident notification is sent");
      } else if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        LOGGER.debug("Incident notification recieved 401 response");
        // retry
        status = notifyOnIncidents(incidents, m2mTokenManager.getToken(true));
        if (status.is2xxSuccessful()) {
          LOGGER.debug("Incident notification is sent");
        } else {
          LOGGER.error("Failed to send incident notification. Response status: " + status);
        }
      } else {
        LOGGER.error("Failed to send incident notification. Response status: " + status);
      }
    } catch (final JsonProcessingException e) {
      LOGGER.error("Failed to create incident notification request: " + e.getMessage(), e);
    } catch (final Exception e) {
      LOGGER.error("Failed to notify on incidents: " + e.getMessage(), e);
    }
  }

  private HttpStatusCode notifyOnIncidents(
      final List<IncidentEntity> incidents, final String m2mToken) throws JsonProcessingException {
    final String webhookURL = operateProperties.getAlert().getWebhook();
    final String payload = getIncidentsAsJSON(incidents);
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.setBearerAuth(m2mToken);

    final HttpEntity<String> request = new HttpEntity<>(payload, headers);
    final ResponseEntity<String> response =
        restTemplate.postForEntity(webhookURL, request, String.class);
    return response.getStatusCode();
  }

  private String getIncidentsAsJSON(final List<IncidentEntity> incidents)
      throws JsonProcessingException {
    final List<Map<String, Object>> incidentList = new ArrayList<>();
    for (final IncidentEntity inc : incidents) {
      final Map<String, Object> incidentFields = new HashMap<>();
      incidentFields.put(FIELD_NAME_MESSAGE, MESSAGE);
      incidentFields.put(FIELD_NAME_ID, inc.getId());
      incidentFields.put(
          FIELD_NAME_PROCESS_INSTANCE_ID, String.valueOf(inc.getProcessInstanceKey()));
      incidentFields.put(FIELD_NAME_CREATION_TIME, inc.getCreationTime());
      incidentFields.put(FIELD_NAME_STATE, inc.getState());
      incidentFields.put(FIELD_NAME_ERROR_MESSAGE, inc.getErrorMessage());
      incidentFields.put(FIELD_NAME_ERROR_TYPE, inc.getErrorType());
      incidentFields.put(FIELD_NAME_FLOW_NODE_ID, inc.getFlowNodeId());
      incidentFields.put(FIELD_NAME_FLOW_NODE_INSTANCE_KEY, inc.getFlowNodeInstanceKey());
      incidentFields.put(FIELD_NAME_JOB_KEY, inc.getJobKey());
      incidentFields.put(FIELD_NAME_PROCESS_KEY, inc.getProcessDefinitionKey());
      final Optional<ProcessEntity> process =
          processCache.findOrWaitProcess(inc.getProcessDefinitionKey(), 2, 1000L);
      if (process.isPresent()) {
        incidentFields.put(FIELD_NAME_BPMN_PROCESS_ID, process.get().getBpmnProcessId());
        incidentFields.put(FIELD_NAME_PROCESS_NAME, process.get().getName());
        incidentFields.put(FIELD_NAME_PROCESS_VERSION, process.get().getVersion());
      }
      incidentList.add(incidentFields);
    }
    return objectMapper.writeValueAsString(asMap(FIELD_NAME_ALERTS, incidentList));
  }
}
