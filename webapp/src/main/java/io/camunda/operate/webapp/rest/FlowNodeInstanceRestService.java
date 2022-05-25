/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.util.CollectionUtil.countNonNullObjects;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import io.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Flow node instances")
@RestController
@RequestMapping(value = FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL)
public class FlowNodeInstanceRestService {

  public static final String FLOW_NODE_INSTANCE_URL = "/api/flow-node-instances";

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @Operation(summary = "Query flow node instance tree. Returns map treePath <-> list of children.")
  @PostMapping
  public Map<String, FlowNodeInstanceResponseDto> queryFlowNodeInstanceTree(@RequestBody FlowNodeInstanceRequestDto request) {
    validateRequest(request);
    return flowNodeInstanceReader.getFlowNodeInstances(request);
  }

  private void validateRequest(final FlowNodeInstanceRequestDto request) {
    if (request.getQueries() == null || request.getQueries().size() == 0) {
      throw new InvalidRequestException("At least one query must be provided when requesting for flow node instance tree.");
    }
    for (FlowNodeInstanceQueryDto query: request.getQueries()) {
      if (query == null || query.getProcessInstanceId() == null || query.getTreePath() == null) {
        throw new InvalidRequestException("Process instance id and tree path must be provided when requesting for flow node instance tree.");
      }
      if (countNonNullObjects(query.getSearchAfter(), query.getSearchAfterOrEqual(),
          query.getSearchBefore(), query.getSearchBeforeOrEqual()) > 1) {
        throw new InvalidRequestException(
            "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
      }
    }
  }

}
