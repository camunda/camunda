/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class PluginJarFileLoader {

  protected final ConfigurationService configurationService;

  public List<Path> getPluginJars() {
    if (!Files.exists(getPluginsDirectoryPath())) {
      log.error("The configured plugin directory [{}] does not exist!", getPluginsDirectoryPath());
      return Collections.emptyList();
    }

    try (Stream<Path> paths = Files.list(getPluginsDirectoryPath())) {
      return paths
        .filter(s -> s.toString().endsWith(".jar"))
        .collect(Collectors.toList());
    } catch (IOException e) {
      log.error("There was an error reading the plugin directory! ");
    }
    return Collections.emptyList();
  }

  private Path getPluginsDirectoryPath() {
    return Paths.get(configurationService.getPluginDirectory());
  }

}
