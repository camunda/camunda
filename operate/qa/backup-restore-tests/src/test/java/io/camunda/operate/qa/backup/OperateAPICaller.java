/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;

import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.net.URI;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class OperateAPICaller {

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate restTemplate;

  public StatefulRestTemplate createRestTemplate(final TestContext testContext) {
    restTemplate =
        statefulRestTemplateFactory.apply(
            testContext.getExternalOperateHost(), testContext.getExternalOperatePort());
    restTemplate.loginWhenNeeded(USERNAME, PASSWORD);
    return restTemplate;
  }

  public ProcessGroupDto[] getGroupedProcesses() {
    return restTemplate.getForObject(
        restTemplate.getURL("/api/processes/grouped"), ProcessGroupDto[].class);
  }

  public ListViewResponseDto getProcessInstances() {
    final ListViewRequestDto processInstanceQueryDto = createGetAllProcessInstancesRequest();
    return restTemplate.postForObject(
        restTemplate.getURL("/api/process-instances"),
        processInstanceQueryDto,
        ListViewResponseDto.class);
  }

  public ListViewResponseDto getIncidentProcessInstances() {
    final ListViewRequestDto processInstanceQueryDto =
        createGetAllProcessInstancesRequest(
            q ->
                q.setIncidents(true)
                    .setActive(false)
                    .setRunning(true)
                    .setCompleted(false)
                    .setCanceled(false)
                    .setFinished(false));
    return restTemplate.postForObject(
        restTemplate.getURL("/api/process-instances"),
        processInstanceQueryDto,
        ListViewResponseDto.class);
  }

  public SequenceFlowDto[] getSequenceFlows(final String processInstanceId) {
    return restTemplate.getForObject(
        restTemplate.getURL("/api/process-instances/" + processInstanceId + "/sequence-flows"),
        SequenceFlowDto[].class);
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
    final Map<String, Object> operationRequest =
        CollectionUtil.asMap("operationType", operationType.name());
    final URI url =
        restTemplate.getURL("/api/process-instances/" + processInstanceKey + "/operation");
    final ResponseEntity<Map> operationResponse =
        restTemplate.postForEntity(url, operationRequest, Map.class);
    return operationResponse.getStatusCode().equals(HttpStatus.OK)
        && operationResponse.getBody().get(BatchOperationTemplate.ID) != null;
  }
}
