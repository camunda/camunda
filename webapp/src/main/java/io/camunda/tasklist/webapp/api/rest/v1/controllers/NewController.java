/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.webapp.es.FormReader;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Form", description = "API to query forms")
@RestController
@RequestMapping(
    value = TasklistURIs.START_PUBLIC_PROCESS,
    produces = MediaType.APPLICATION_JSON_VALUE)
public class NewController extends ApiErrorController {
  @Autowired private FormReader formReader;

  @RequestMapping("/")
  @ResponseBody
  public String newError() {
    return "No permission for Tasklist - Please check your configuration.";
  }
}
