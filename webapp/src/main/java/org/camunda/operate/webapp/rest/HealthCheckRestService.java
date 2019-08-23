/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import org.camunda.operate.webapp.rest.dto.HealthStateDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class HealthCheckRestService {

  public static final String HEALTH_CHECK_URL = "/api/check";

  @RequestMapping(value = HEALTH_CHECK_URL, method = RequestMethod.GET)
  public HealthStateDto status() {
    return new HealthStateDto("OK");
  }

}
