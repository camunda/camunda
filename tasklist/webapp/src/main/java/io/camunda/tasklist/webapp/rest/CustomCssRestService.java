/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.rest;

import com.google.common.base.Charsets;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomCssRestService {

  public static final String TASKLIST_CUSTOM_CSS = "/tasklist/custom.css";

  // TODO: prepare for docker
  public static final String PATH_LOCATION = "custom.css";
  private static final Logger LOGGER = LoggerFactory.getLogger(CustomCssRestService.class);

  private String customCssContent;

  @PostConstruct
  public void init() {
    try {
      final Resource resource = loadResource(PATH_LOCATION);
      customCssContent = new String(resource.getContentAsString(Charsets.UTF_8));
    } catch (final IOException e) {
      LOGGER.error("Error when reading custom css file {}", PATH_LOCATION, e);
      customCssContent = "";
    }
  }

  @Hidden
  @GetMapping(path = TASKLIST_CUSTOM_CSS, produces = "text/css")
  public String getClientConfig() {
    return customCssContent;
  }

  public Resource loadResource(final String path) {
    return new ClassPathResource(path);
  }

  public String getCustomCssContent() {
    return customCssContent;
  }
}
