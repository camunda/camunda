/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.backup;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;

@Component
public class OperateAPICaller {

  private final static String USERNAME = "demo";
  private final static String PASSWORD = "demo";

  @Autowired
  private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate restTemplate;

  public StatefulRestTemplate createRestTemplate(TestContext testContext) {
    restTemplate = statefulRestTemplateFactory.apply(testContext.getExternalOperateHost(), testContext.getExternalOperatePort());
    restTemplate.loginWhenNeeded(USERNAME, PASSWORD);
    return restTemplate;
  }

  public ProcessGroupDto[] getGroupedProcesses() {
    return restTemplate.getForObject(restTemplate.getURL("/api/processes/grouped"),
        ProcessGroupDto[].class);
  }

  public ListViewResponseDto getProcessInstances() {
    ListViewRequestDto processInstanceQueryDto = createGetAllProcessInstancesRequest();
    return restTemplate.postForObject(restTemplate.getURL("/api/process-instances"), processInstanceQueryDto,
        ListViewResponseDto.class);
  }
  public ListViewResponseDto getIncidentProcessInstances() {
    ListViewRequestDto processInstanceQueryDto = createGetAllProcessInstancesRequest( q -> q.setIncidents(true)
        .setActive(false).setRunning(true).setCompleted(false).setCanceled(false).setFinished(false));
    return restTemplate.postForObject(restTemplate.getURL("/api/process-instances"), processInstanceQueryDto,
        ListViewResponseDto.class);
  }

  public SequenceFlowDto[] getSequenceFlows(String processInstanceId) {
    return restTemplate.getForObject(
        restTemplate.getURL("/api/process-instances/" + processInstanceId + "/sequence-flows"),
        SequenceFlowDto[].class);
  }

  public TakeBackupResponseDto backup(Integer backupId) {
    TakeBackupRequestDto takeBackupRequest = new TakeBackupRequestDto().setBackupId(backupId);
    return restTemplate.postForObject(restTemplate.getURL("/actuator/backups"), takeBackupRequest,
        TakeBackupResponseDto.class);
  }

  public GetBackupStateResponseDto getBackupState(Integer backupId) {
    return restTemplate.getForObject(restTemplate.getURL("/actuator/backups/" + backupId),
        GetBackupStateResponseDto.class);
  }

  boolean createOperation(Long processInstanceKey, OperationType operationType) {
    Map<String, Object> operationRequest = CollectionUtil.asMap("operationType", operationType.name());
    final URI url = restTemplate.getURL("/api/process-instances/" + processInstanceKey + "/operation");
    ResponseEntity<Map> operationResponse = restTemplate.postForEntity(url, operationRequest, Map.class);
    return operationResponse.getStatusCode().equals(HttpStatus.OK) && operationResponse.getBody().get(
        BatchOperationTemplate.ID) != null;
  }
}
