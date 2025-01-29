/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.search.entities.AlertDefinitionEntity;
import io.camunda.service.AlertDefinitionServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v2/incident-alerting"})
public class IncidentAlertingController {

  private final AlertDefinitionServices alertDefinitionServices;

  public IncidentAlertingController(final AlertDefinitionServices alertDefinitionServices) {
    this.alertDefinitionServices = alertDefinitionServices;
  }

  @CamundaPostMapping(path = "/config")
  public ResponseEntity<Object> store(@RequestBody final AlertDefinitionEntity alertDefinition) {
    alertDefinitionServices.store(alertDefinition);
    return ResponseEntity.ok().build();
  }

  @CamundaGetMapping()
  public ResponseEntity<Object> query() {
    return ResponseEntity.ok(alertDefinitionServices.query());
  }
}
