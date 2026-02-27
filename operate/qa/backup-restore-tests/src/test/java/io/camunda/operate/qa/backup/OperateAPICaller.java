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
import io.camunda.operate.webapp.reader.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.reader.dto.listview.ListViewResponseDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

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
    return searchProcessInstances(new ProcessInstancesSearchRequest(1000, null));
  }

  public ListViewResponseDto getIncidentProcessInstances() {
    return searchProcessInstances(
        new ProcessInstancesSearchRequest(
            1000, new ProcessInstanceSearchFilter(ProcessInstanceState.ACTIVE.name(), true)));
  }

  public String[] getSequenceFlows(final String processInstanceId) {
    final ProcessInstanceSequenceFlowsQueryResult response =
        restTemplate.getForObject(
            restTemplate.getURL("/v2/process-instances/" + processInstanceId + "/sequence-flows"),
            ProcessInstanceSequenceFlowsQueryResult.class);
    if (response == null || response.items() == null) {
      return new String[0];
    }
    return response.items().stream()
        .map(ProcessInstanceSequenceFlowResult::sequenceFlowId)
        .toArray(String[]::new);
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
    final Map<String, Object> operationRequest = Map.of("operationType", operationType.name());
    final URI url =
        restTemplate.getURL("/api/process-instances/" + processInstanceKey + "/operation");
    try {
      final ResponseEntity<Map> operationResponse =
          restTemplate.postForEntity(url, operationRequest, Map.class);
      return operationResponse.getStatusCode().equals(HttpStatus.OK)
          && operationResponse.getBody() != null
          && operationResponse.getBody().get("id") != null;
    } catch (final RestClientException e) {
      return false;
    }
  }

  private ListViewResponseDto searchProcessInstances(final ProcessInstancesSearchRequest query) {
    final Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("page", Map.of("limit", query.size()));
    final ProcessInstanceSearchFilter filter = query.filter();
    if (filter != null) {
      final Map<String, Object> filterBody = new HashMap<>();
      if (filter.state() != null) {
        filterBody.put("state", filter.state());
      }
      if (filter.hasIncident() != null) {
        filterBody.put("hasIncident", filter.hasIncident());
      }
      if (!filterBody.isEmpty()) {
        requestBody.put("filter", filterBody);
      }
    }

    final RequestEntity<Map<String, Object>> request =
        RequestEntity.post(restTemplate.getURL("/v2/process-instances/search"))
            .headers(restTemplate.getHeaders())
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody);
    final ResponseEntity<ProcessInstanceSearchQueryResult> response =
        restTemplate.exchange(request, ProcessInstanceSearchQueryResult.class);
    final ProcessInstanceSearchQueryResult results = response.getBody();

    final ListViewResponseDto listViewResponse = new ListViewResponseDto();
    if (results == null || results.items() == null) {
      return listViewResponse;
    }

    if (results.page() != null && results.page().totalItems() != null) {
      listViewResponse.setTotalCount(results.page().totalItems());
    }
    listViewResponse.setProcessInstances(
        results.items().stream()
            .map(
                processInstance ->
                    new ListViewProcessInstanceDto()
                        .setId(String.valueOf(processInstance.processInstanceKey()))
                        .setBpmnProcessId(processInstance.processDefinitionId()))
            .toList());
    return listViewResponse;
  }

  private record ProcessInstancesSearchRequest(int size, ProcessInstanceSearchFilter filter) {}

  private record ProcessInstanceSearchFilter(String state, Boolean hasIncident) {}

  private record ProcessInstanceSearchQueryResult(
      List<ProcessInstanceSearchResultItem> items, ProcessInstanceSearchPage page) {}

  private record ProcessInstanceSearchResultItem(
      Long processInstanceKey, String processDefinitionId) {}

  private record ProcessInstanceSearchPage(Long totalItems) {}

  private record ProcessInstanceSequenceFlowsQueryResult(
      List<ProcessInstanceSequenceFlowResult> items) {}

  private record ProcessInstanceSequenceFlowResult(String sequenceFlowId) {}
}
