/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.IncidentServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("v2/incidents")
public class IncidentController {

  private final ResponseObserverProvider responseObserverProvider;
  private final IncidentServices incidentServices;

  @Autowired
  public IncidentController(
      final IncidentServices incidentServices,
      final ResponseObserverProvider responseObserverProvider
  ) {
    this.incidentServices = incidentServices;
    this.responseObserverProvider = responseObserverProvider;
  }
}
