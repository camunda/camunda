/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.license;

import io.camunda.service.ManagementService;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ZeebeRestController
@RequestMapping(path = {"/v1", "/v2"})
public final class LicenseController {

  @Autowired private ManagementService managementService;

  @GetMapping(path = "/camunda-license", produces = MediaType.APPLICATION_JSON_VALUE)
  public boolean get() {
    return managementService.isCamundaLicenseValid();
  }
}
