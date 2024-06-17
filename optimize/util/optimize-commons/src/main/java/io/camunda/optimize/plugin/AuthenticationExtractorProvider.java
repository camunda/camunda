/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin;

import io.camunda.optimize.plugin.security.authentication.AuthenticationExtractor;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationExtractorProvider extends PluginProvider<AuthenticationExtractor> {

  public AuthenticationExtractorProvider(
      final ConfigurationService configurationService, final PluginJarFileLoader pluginJarLoader) {
    super(configurationService, pluginJarLoader);
  }

  @Override
  protected Class<AuthenticationExtractor> getPluginClass() {
    return AuthenticationExtractor.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getAuthenticationExtractorPluginBasePackages();
  }
}
