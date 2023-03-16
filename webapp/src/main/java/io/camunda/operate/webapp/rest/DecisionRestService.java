/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.es.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Decisions")
@RestController
@RequestMapping(value = DecisionRestService.DECISION_URL)
public class DecisionRestService extends InternalAPIErrorController {

  @Autowired
  protected DecisionReader decisionReader;

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  public static final String DECISION_URL = "/api/decisions";

  @Operation(summary = "Get process BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getDecisionDiagram(@PathVariable("id") String decisionDefinitionId) {
    checkIdentityReadPermission(decisionDefinitionId);
    return decisionReader.getDiagram(decisionDefinitionId);
  }

  @Operation(summary = "List processes grouped by decisionId")
  @GetMapping(path = "/grouped")
  public List<DecisionGroupDto> getDecisionsGrouped() {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped = decisionReader.getDecisionsGrouped();
    return DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);
  }

  private void checkIdentityReadPermission(String decisionDefinitionId) {
    if (permissionsService != null) {
      String bpmnDecisionId = decisionReader.getDecision(Long.valueOf(decisionDefinitionId)).getDecisionId();
      if (!permissionsService.hasPermissionForDecision(bpmnDecisionId, IdentityPermission.READ)) {
        throw new NotAuthorizedException(String.format("No read permission for decision %s", decisionDefinitionId));
      }
    }
  }
}
