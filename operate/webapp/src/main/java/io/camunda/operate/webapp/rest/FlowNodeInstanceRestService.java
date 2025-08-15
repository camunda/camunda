/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.util.CollectionUtil.countNonNullObjects;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Flow node instances")
@RestController
@RequestMapping(value = FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL)
public class FlowNodeInstanceRestService extends InternalAPIErrorController {

  public static final String FLOW_NODE_INSTANCE_URL = "/api/flow-node-instances";

  @Autowired private PermissionsService permissionsService;
  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;
  @Autowired private ProcessInstanceReader processInstanceReader;

  @Operation(summary = "Query flow node instance tree. Returns map treePath <-> list of children.")
  @PostMapping
  public Map<String, FlowNodeInstanceResponseDto> queryFlowNodeInstanceTree(
      @RequestBody final FlowNodeInstanceRequestDto request) {
    validateRequest(request);
    checkIdentityReadPermission(Long.parseLong(request.getQueries().get(0).getProcessInstanceId()));
    return flowNodeInstanceReader.getFlowNodeInstances(request);
  }

  private void validateRequest(final FlowNodeInstanceRequestDto request) {
    if (request.getQueries() == null || request.getQueries().size() == 0) {
      throw new InvalidRequestException(
          "At least one query must be provided when requesting for flow node instance tree.");
    }

    String processInstanceId = null;
    for (final FlowNodeInstanceQueryDto query : request.getQueries()) {
      if (query == null || query.getProcessInstanceId() == null || query.getTreePath() == null) {
        throw new InvalidRequestException(
            "Process instance id and tree path must be provided when requesting for flow node instance tree.");
      }
      if (countNonNullObjects(
              query.getSearchAfter(),
              query.getSearchAfterOrEqual(),
              query.getSearchBefore(),
              query.getSearchBeforeOrEqual())
          > 1) {
        throw new InvalidRequestException(
            "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
      }
      if (processInstanceId == null) {
        processInstanceId = query.getProcessInstanceId();
      } else if (!Objects.equals(processInstanceId, query.getProcessInstanceId())) {
        throw new InvalidRequestException(
            "Process instance id must be the same for all the queries.");
      }
    }
  }

  private void checkIdentityReadPermission(final Long processInstanceKey) {
    if (!permissionsService.hasPermissionForProcess(
        processInstanceReader.getProcessInstanceByKey(processInstanceKey).getBpmnProcessId(),
        PermissionType.READ_PROCESS_INSTANCE)) {
      throw new NotAuthorizedException(
          String.format("No read permission for process instance %s", processInstanceKey));
    }
  }
}
