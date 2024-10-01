/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class RootUrlGenerator {

  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(RootUrlGenerator.class);

  private final ConfigurationService configurationService;

  public RootUrlGenerator(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public String getRootUrl() {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();
    if (containerAccessUrl.isPresent()) {
      return containerAccessUrl.get();
    } else {
      final Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      final String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      final Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      return httpPrefix
          + configurationService.getContainerHost()
          + ":"
          + port
          + configurationService.getContextPath().orElse("");
    }
  }
}
