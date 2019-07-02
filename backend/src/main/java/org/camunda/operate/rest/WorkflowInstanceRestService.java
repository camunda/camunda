/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;

import java.util.Collection;
import java.util.List;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.SequenceFlowReader;
import org.camunda.operate.es.reader.VariableReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.rest.dto.SequenceFlowDto;
import org.camunda.operate.rest.dto.VariableDto;
import org.camunda.operate.rest.dto.WorkflowInstanceCoreStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@Api(tags = {"Workflow instances"})
@SwaggerDefinition(tags = {
  @Tag(name = "Workflow instances", description = "Workflow instances")
})
@RestController
@RequestMapping(value = WORKFLOW_INSTANCE_URL)
public class WorkflowInstanceRestService {

  public static final String WORKFLOW_INSTANCE_URL = "/api/workflow-instances";

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ListViewReader listViewReader;
  
  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private VariableReader variableReader;

  @Autowired
  private SequenceFlowReader sequenceFlowReader;

  @ApiOperation("Query workflow instances by different parameters")
  @PostMapping
  public ListViewResponseDto queryWorkflowInstances(
      @RequestBody ListViewRequestDto workflowInstanceRequest,
      @RequestParam("firstResult") Integer firstResult,   //required
      @RequestParam("maxResults") Integer maxResults) {   //required
    for (ListViewQueryDto query : workflowInstanceRequest.getQueries()) {
      if (query.getWorkflowVersion() != null && query.getBpmnProcessId() == null) {
        throw new InvalidRequestException("BpmnProcessId must be provided in request, when workflow version is not null.");
      }
    }
    return listViewReader.queryWorkflowInstances(workflowInstanceRequest, firstResult, maxResults);
  }

  @ApiOperation("Perform batch operation on an instance (async)")
  @PostMapping("/{id}/operation")
  public OperationResponseDto operation(@PathVariable String id,
      @RequestBody OperationRequestDto operationRequest) throws PersistenceException {
    validateOperationRequest(operationRequest);
    return batchOperationWriter.scheduleOperation(Long.valueOf(id), operationRequest);
  }

  private void validateOperationRequest(OperationRequestDto operationRequest) {
    if (operationRequest.getOperationType() == null) {
      throw new InvalidRequestException("Operation type must be defined.");
    }
    if (operationRequest.getOperationType().equals(OperationType.UPDATE_VARIABLE)
      && (operationRequest.getScopeId() == null || operationRequest.getName() == null || operationRequest.getName().isEmpty()
        || operationRequest.getValue() == null)) {
        throw new InvalidRequestException("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
    }
  }

  @ApiOperation("Perform batch operation on selection (async)")
  @PostMapping("/operation")
  public OperationResponseDto batchOperation(
      @RequestBody BatchOperationRequestDto batchOperationRequest) {
    return batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  @ApiOperation("Get workflow instance by id")
  @GetMapping("/{id}")
  public ListViewWorkflowInstanceDto queryWorkflowInstanceById(@PathVariable String id) {
    return workflowInstanceReader.getWorkflowInstanceWithOperationsById(Long.valueOf(id));
  }

  @ApiOperation("Get incidents by workflow instance id")
  @GetMapping("/{id}/incidents")
  public IncidentResponseDto queryIncidentsByWorkflowInstanceId(@PathVariable String id) {
    return incidentReader.getIncidents(Long.valueOf(id));
  }

  @ApiOperation("Get sequence flows by workflow instance id")
  @GetMapping("/{id}/sequence-flows")
  public List<SequenceFlowDto> querySequenceFlowsByWorkflowInstanceId(@PathVariable String id) {
    final List<SequenceFlowEntity> sequenceFlows = sequenceFlowReader.getSequenceFlows(id);
    return SequenceFlowDto.createFrom(sequenceFlows);
  }

  @ApiOperation("Get variables by workflow instance id and scope id")
  @GetMapping("/{workflowInstanceId}/variables")
  public List<VariableDto> getVariables(@PathVariable String workflowInstanceId, @RequestParam String scopeId) {
    return variableReader.getVariables(Long.valueOf(workflowInstanceId), scopeId);
  }

  @ApiOperation("Get activity instance statistics")
  @PostMapping(path = "/statistics")
  public Collection<ActivityStatisticsDto> getStatistics(@RequestBody ListViewRequestDto workflowInstanceRequest) {
    final List<ListViewQueryDto> queries = workflowInstanceRequest.getQueries();
    if (queries.size() != 1) {
      throw new InvalidRequestException("Exactly one query must be specified in the request.");
    }
    final List<Long> workflowIds = CollectionUtil.toSafeListOfLongs(queries.get(0).getWorkflowIds());
    final String bpmnProcessId = queries.get(0).getBpmnProcessId();
    final Integer workflowVersion = queries.get(0).getWorkflowVersion();

    if ( (workflowIds != null && workflowIds.size() == 1) == (bpmnProcessId != null && workflowVersion != null) ) {
      throw new InvalidRequestException("Exactly one workflow must be specified in the request (via workflowIds or bpmnProcessId/version).");
    }
    return listViewReader.getActivityStatistics(queries.get(0));
  }
  
  @ApiOperation("Get workflow instance core statistics (aggregations)")
  @GetMapping(path = "/core-statistics")
  public WorkflowInstanceCoreStatisticsDto getCoreStatistics() {
      return workflowInstanceReader.getCoreStatistics();
  }

}
