/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.DecisionInstanceRestService.DECISION_INSTANCE_URL;

import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.es.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = {"Decision instances"})
@SwaggerDefinition(tags = {
    @Tag(name = "Decision instances", description = "Decision instances")
})
@RestController
@RequestMapping(value = DECISION_INSTANCE_URL)
@Validated
public class DecisionInstanceRestService {

  public static final String DECISION_INSTANCE_URL = "/api/decision-instances";

  @Autowired
  private DecisionInstanceReader decisionInstanceReader;

  @ApiOperation("Get decision instance by id")
  @GetMapping("/{decisionInstanceId}")
  public DecisionInstanceDto queryProcessInstanceById(@PathVariable @ValidLongId String decisionInstanceId) {
    return decisionInstanceReader.getDecisionInstance(decisionInstanceId);
  }

}
