/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
      return paths.filter(s -> s.toString().endsWith(".jar")).collect(Collectors.toList());
    } catch (IOException e) {
      log.error("There was an error reading the plugin directory! ");
    }
    return Collections.emptyList();
  }

  private Path getPluginsDirectoryPath() {
    return Paths.get(configurationService.getPluginDirectory());
  }
}
