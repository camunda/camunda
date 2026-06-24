/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebappUiEnabled("tmp-webapp")
public class CustomCssController {

  static final String PATH = "/webapp/custom.css";

  static final String CLASSPATH_LOCATION = "custom.css";

  private static final Logger LOG = LoggerFactory.getLogger(CustomCssController.class);

  private String customCssContent;

  @PostConstruct
  public void init() {
    final Resource resource = loadResource(CLASSPATH_LOCATION);
    if (!resource.exists()) {
      LOG.debug("No custom.css found on classpath — custom styles disabled");
      customCssContent = "";
      return;
    }
    try {
      customCssContent = resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (final IOException e) {
      LOG.warn("Failed to read custom css file {}, custom styles disabled", CLASSPATH_LOCATION, e);
      customCssContent = "";
    }
  }

  @GetMapping(path = PATH, produces = "text/css")
  public ResponseEntity<String> getCustomCss() {
    return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(customCssContent);
  }

  public Resource loadResource(final String path) {
    return new ClassPathResource(path);
  }

  public String getCustomCssContent() {
    return customCssContent;
  }
}
