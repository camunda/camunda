/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Decisions")
@RestController
@RequestMapping(value = DecisionRestService.DECISION_URL)
@ConditionalOnRdbmsDisabled
public class DecisionRestService extends InternalAPIErrorController {

  public static final String DECISION_URL = "/api/decisions";

  @Autowired private DecisionReader decisionReader;
  @Autowired private PermissionsService permissionsService;

  @Operation(summary = "List decisions grouped by decisionId")
  @GetMapping(path = "/grouped")
  @Deprecated
  public List<DecisionGroupDto> getDecisionsGrouped() {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped =
        decisionReader.getDecisionsGrouped(new DecisionRequestDto());
    return DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);
  }

  @Operation(summary = "List decisions grouped by decisionId")
  @PostMapping(path = "/grouped")
  public List<DecisionGroupDto> getDecisionsGrouped(@RequestBody final DecisionRequestDto request) {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped =
        decisionReader.getDecisionsGrouped(request);
    return DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);
  }
}
