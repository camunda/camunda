/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.rest;

import io.zeebe.tasklist.Probes;
import io.zeebe.tasklist.webapp.rest.dto.HealthStateDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static io.zeebe.tasklist.webapp.rest.dto.HealthStateDto.HEALTH_STATUS_OK;

@RestController
public class HealthCheckRestService {

  public static final String HEALTH_CHECK_URL = "/api/check";

  @Autowired
  private Probes probes;

  @RequestMapping(value = HEALTH_CHECK_URL, method = RequestMethod.GET)
  public HealthStateDto status(@RequestParam(name = "maxDuration", required = false, defaultValue = "50000") Long maxDurationInMs) {
    if (probes.isLive(maxDurationInMs)) {
      return new HealthStateDto(HEALTH_STATUS_OK);
    } else {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

}
