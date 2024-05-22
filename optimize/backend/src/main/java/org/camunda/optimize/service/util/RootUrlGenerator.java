/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class RootUrlGenerator {

  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";

  private final ConfigurationService configurationService;

  public String getRootUrl() {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();
    if (containerAccessUrl.isPresent()) {
      return containerAccessUrl.get();
    } else {
      Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      return httpPrefix
          + configurationService.getContainerHost()
          + ":"
          + port
          + configurationService.getContextPath().orElse("");
    }
  }
}
