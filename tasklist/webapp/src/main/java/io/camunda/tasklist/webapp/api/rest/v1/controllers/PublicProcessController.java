/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.ProcessEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping(value = TasklistURIs.START_PUBLIC_PROCESS)
public class PublicProcessController extends ApiErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(PublicProcessController.class);

  @Autowired private ProcessStore processStore;

  @GetMapping("/{bpmnProcessId}")
  public ModelAndView index(
      final Model model, @PathVariable final String bpmnProcessId, final HttpServletRequest req) {
    final ModelAndView modelAndView = new ModelAndView("forward:/tasklist/index.html");
    modelAndView.setStatus(HttpStatus.OK);
    try {
      final ProcessEntity processEntity = processStore.getProcessByBpmnProcessId(bpmnProcessId);
      final String title =
          processEntity.getName() != null
              ? processEntity.getName()
              : processEntity.getBpmnProcessId();

      modelAndView.addObject("title", title);
    } catch (final NotFoundException ex) {
      LOGGER.warn("Could not find process with id {}", bpmnProcessId);
      LOGGER.debug("StackTrace:", ex);
    }
    modelAndView.addObject("ogImage", getAbsolutePathOfImage(req));
    modelAndView.addObject("ogUrl", req.getRequestURL().toString());

    return modelAndView;
  }

  private String getAbsolutePathOfImage(final HttpServletRequest request) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.newInstance()
            .scheme(request.getScheme())
            .host(request.getServerName())
            .path(request.getContextPath())
            .path("/public-start-form-og-image.jpg");

    return builder.toUriString();
  }
}
