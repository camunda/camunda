/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class ForwardErrorController implements ErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);

  @RequestMapping("/error")
  public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
    LOGGER.warn("Requested non existing path. Forward (on serverside) to /");
    final ModelAndView modelAndView = new ModelAndView("forward:/");
    // Is it really necessary to set status to OK (for frontend)?
    modelAndView.setStatus(HttpStatus.OK);
    return modelAndView;
  }

  @Override
  public String getErrorPath() {
    return "/error";
  }
}
