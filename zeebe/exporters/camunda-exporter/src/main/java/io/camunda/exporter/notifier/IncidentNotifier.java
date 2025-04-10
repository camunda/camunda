/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.notifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.config.ExporterConfiguration.IncidentNotifierConfiguration;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class that sends notifications about the created incidents on configured URL. */
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

  private final M2mTokenManager m2mTokenManager;

  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final IncidentNotifierConfiguration configuration;
  private final HttpClient httpClient;
  private final Executor executor;
  private final ObjectWriter objectWriter;

  public IncidentNotifier(
      final M2mTokenManager m2mTokenManager,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final IncidentNotifierConfiguration configuration,
      final HttpClient httpClient,
      final Executor executor,
      final ObjectMapper objectMapper) {
    this.m2mTokenManager = m2mTokenManager;
    this.processCache = processCache;
    this.configuration = configuration;
    this.httpClient = httpClient;
    this.executor = executor;
    objectWriter = objectMapper.writer();
  }

  public void notifyAsync(final List<IncidentEntity> incidents) {
    CompletableFuture.runAsync(() -> notifyOnIncidents(incidents), executor);
    LOGGER.debug("Incident notification is scheduled");
  }

  public void notifyOnIncidents(final List<IncidentEntity> incidents) {
    if (StringUtils.isBlank(configuration.getWebhook())) {
      LOGGER.debug("Incident notification is disabled");
      return;
    }

    try {
      int status = notifyOnIncidents(incidents, m2mTokenManager.getToken());

      if (status / 100 == 2) {
        LOGGER.debug("Incident notification is sent");
      } else if (status == 401) {
        LOGGER.debug("Incident notification unauthorized. Retrying with a new token");
        // retry
        status = notifyOnIncidents(incidents, m2mTokenManager.getToken(true));
        if (status / 100 == 2) {
          LOGGER.debug("Incident notification is sent");
        } else {
          LOGGER.warn(
              "Failed to send incident notification after retrying with a new token. Response status: "
                  + status);
        }
      } else {
        LOGGER.warn("Failed to send incident notification. Response status: " + status);
      }
    } catch (final JsonProcessingException e) {
      LOGGER.warn("Failed to create incident notification request: " + e.getMessage(), e);
    } catch (final Exception e) {
      LOGGER.warn("Failed to notify on incidents: " + e.getMessage(), e);
    }
  }

  private int notifyOnIncidents(final List<IncidentEntity> incidents, final String m2mToken)
      throws IOException, InterruptedException {
    final String payload = getIncidentsAsJSON(incidents);
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(configuration.getWebhook()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + m2mToken)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    return response.statusCode();
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
      final Optional<CachedProcessEntity> process = processCache.get(inc.getProcessDefinitionKey());
      if (process.isPresent()) {
        incidentFields.put(FIELD_NAME_BPMN_PROCESS_ID, inc.getBpmnProcessId());
        incidentFields.put(FIELD_NAME_PROCESS_NAME, process.get().name());
        incidentFields.put(FIELD_NAME_PROCESS_VERSION, process.get().versionTag());
      }
      incidentList.add(incidentFields);
    }
    return objectWriter.writeValueAsString(Map.of(FIELD_NAME_ALERTS, incidentList));
  }
}
