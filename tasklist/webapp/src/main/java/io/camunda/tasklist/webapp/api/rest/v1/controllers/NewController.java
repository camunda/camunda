/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.webapp.security.TasklistURIs;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping(value = TasklistURIs.START_PUBLIC_PROCESS)
public class NewController {

  @Autowired private ServletContext context;

  @GetMapping("/{bpmnProcessId}")
  public ModelAndView index(Model model, @PathVariable String bpmnProcessId) {
    final ModelAndView modelAndView = new ModelAndView("forward:/index.html");
    modelAndView.setStatus(HttpStatus.OK);

    return modelAndView;
  }
}
