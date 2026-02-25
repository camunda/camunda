/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.testhelpers.StatefulRestTemplate;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.reader.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.reader.dto.listview.ListViewResponseDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.net.URI;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

@Component
public class OperateAPICaller {

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate restTemplate;

  public StatefulRestTemplate createRestTemplate(final TestContext testContext) {
    restTemplate =
        statefulRestTemplateFactory.apply(
            testContext.getExternalOperateHost(), testContext.getExternalOperatePort());
    return restTemplate;
  }

  public ListViewResponseDto getProcessInstances() {
    return searchProcessInstances(new Query<ProcessInstance>().setSize(1000));
  }

  public ListViewResponseDto getIncidentProcessInstances() {
    final Query<ProcessInstance> query =
        new Query<ProcessInstance>()
            .setSize(1000)
            .setFilter(
                new ProcessInstance()
                    .setState(ProcessInstanceState.ACTIVE.name())
                    .setIncident(true));
    return searchProcessInstances(query);
  }

  public String[] getSequenceFlows(final String processInstanceId) {
    return restTemplate.getForObject(
        restTemplate.getURL("/v1/process-instances/" + processInstanceId + "/sequence-flows"),
        String[].class);
  }

  public TakeBackupResponseDto backup(final Long backupId) {
    final TakeBackupRequestDto takeBackupRequest = new TakeBackupRequestDto().setBackupId(backupId);
    return restTemplate.postForObject(
        restTemplate.getURL("/actuator/backups"), takeBackupRequest, TakeBackupResponseDto.class);
  }

  public GetBackupStateResponseDto getBackupState(final Long backupId) {
    return restTemplate.getForObject(
        restTemplate.getURL("/actuator/backups/" + backupId), GetBackupStateResponseDto.class);
  }

  boolean createOperation(final Long processInstanceKey, final OperationType operationType) {
    final String operationPath =
        switch (operationType) {
          case CANCEL_PROCESS_INSTANCE -> "/v2/process-instances/%d/cancellation";
          case RESOLVE_INCIDENT -> "/v2/process-instances/%d/incident-resolution";
          default ->
              throw new IllegalArgumentException(
                  "Unsupported operation type for backup data generator: " + operationType);
        };
    final URI url = restTemplate.getURL(operationPath.formatted(processInstanceKey));
    try {
      final ResponseEntity<String> operationResponse =
          restTemplate.postForEntity(url, null, String.class);
      return switch (operationType) {
        case CANCEL_PROCESS_INSTANCE ->
            operationResponse.getStatusCode().equals(HttpStatus.NO_CONTENT);
        case RESOLVE_INCIDENT ->
            operationResponse.getStatusCode().equals(HttpStatus.OK)
                && operationResponse.getBody() != null
                && operationResponse.getBody().contains("batchOperationKey");
        default -> false;
      };
    } catch (final HttpStatusCodeException e) {
      return false;
    }
  }

  private ListViewResponseDto searchProcessInstances(final Query<ProcessInstance> query) {
    final RequestEntity<Query<ProcessInstance>> request =
        RequestEntity.post(restTemplate.getURL("/v1/process-instances/search"))
            .headers(restTemplate.getHeaders())
            .contentType(MediaType.APPLICATION_JSON)
            .body(query);
    final ResponseEntity<Results<ProcessInstance>> response =
        restTemplate.exchange(
            request, new ParameterizedTypeReference<Results<ProcessInstance>>() {});
    final Results<ProcessInstance> results = response.getBody();

    final ListViewResponseDto listViewResponse = new ListViewResponseDto();
    if (results == null || results.getItems() == null) {
      return listViewResponse;
    }

    listViewResponse.setTotalCount(results.getTotal());
    listViewResponse.setProcessInstances(
        results.getItems().stream()
            .map(
                processInstance ->
                    new ListViewProcessInstanceDto()
                        .setId(String.valueOf(processInstance.getKey()))
                        .setBpmnProcessId(processInstance.getBpmnProcessId()))
            .toList());
    return listViewResponse;
  }
}
