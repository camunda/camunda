/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.es.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = {"Decisions"})
@SwaggerDefinition(tags = {
  @Tag(name = "Decisions", description = "Decisions")
})
@RestController
@RequestMapping(value = DecisionRestService.DECISION_URL)
public class DecisionRestService {

  @Autowired
  protected DecisionReader decisionReader;

  public static final String DECISION_URL = "/api/decisions";


  @ApiOperation("Get process BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getDecisionDiagram(@PathVariable("id") String decisionDefinitionId) {
    return decisionReader.getDiagram(decisionDefinitionId);
  }

  @ApiOperation("List processes grouped by decisionId")
  @GetMapping(path = "/grouped")
  public List<DecisionGroupDto> getDecisionsGrouped() {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped = decisionReader.getDecisionsGrouped();
    return DecisionGroupDto.createFrom(decisionsGrouped);
  }

}
