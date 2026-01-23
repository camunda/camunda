/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@CamundaRestController
public class GlobalErrorController implements ErrorController {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalErrorController.class);

  private final ErrorAttributes errorAttributes;

  public GlobalErrorController(final ErrorAttributes errorAttributes) {
    this.errorAttributes = errorAttributes;
  }

  @RequestMapping("/error")
  public ResponseEntity<ProblemDetail> handleError(final HttpServletRequest request) {
    final WebRequest webRequest = new ServletWebRequest(request);
    final ErrorAttributeOptions options =
        ErrorAttributeOptions.of(Include.MESSAGE, Include.PATH, Include.STATUS);

    final Map<String, Object> attributes = errorAttributes.getErrorAttributes(webRequest, options);
    LOG.trace("GlobalErrorController: error attributes = {}", attributes);

    final String path = (String) attributes.getOrDefault("path", request.getRequestURI());
    final int status = (int) attributes.getOrDefault("status", 500);
    final String detail = (String) attributes.getOrDefault("message", "No message available");

    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
    problemDetail.setInstance(URI.create(path != null ? path : "/unknown"));
    return RestErrorMapper.mapProblemToResponse(problemDetail);
  }
}
