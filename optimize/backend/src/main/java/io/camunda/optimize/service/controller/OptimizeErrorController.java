/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OptimizeErrorController implements ErrorController {

  @RequestMapping("/error")
  public ResponseEntity<String> handleError(final HttpServletRequest request) {
    final Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    final int status = statusAttr != null ? Integer.parseInt(statusAttr.toString()) : 500;

    if (status == HttpStatus.FORBIDDEN.value()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .contentType(MediaType.TEXT_PLAIN)
          .body(
              "User has no authorization to access Optimize. Please check your Identity configuration");
    }

    // fall back to default Spring Boot whitelabel behavior for anything else
    return ResponseEntity.status(status).body("");
  }
}
