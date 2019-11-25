/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import org.camunda.operate.webapp.es.reader.ActivityInstanceReader;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.webapp.rest.ActivityInstanceRestService.ACTIVITY_INSTANCE_URL;

@Api(tags = {"Activity instances"})
@SwaggerDefinition(tags = {
  @Tag(name = "Activity instances", description = "Activity instances")
})
@RestController
@RequestMapping(value = ACTIVITY_INSTANCE_URL)
public class ActivityInstanceRestService {

  public static final String ACTIVITY_INSTANCE_URL = "/api/activity-instances";

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @ApiOperation("Query activity instance tree")
  @PostMapping
  public ActivityInstanceTreeDto queryActivityInstanceTree(@RequestBody ActivityInstanceTreeRequestDto request) {
    if (request == null || request.getWorkflowInstanceId() == null) {
      throw new InvalidRequestException("Workflow instance id must be provided when requesting for activity instance tree.");
    }
    return activityInstanceReader.getActivityInstanceTree(request);
  }

}
