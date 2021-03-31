/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.EngineRestFilterProvider;
import org.camunda.optimize.plugin.engine.rest.EngineRestFilter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
public class EngineContextFactory {

  private final ConfigurationService configurationService;
  private final EngineObjectMapperContextResolver engineObjectMapperContextResolver;
  private final EngineRestFilterProvider engineRestFilterProvider;

  private Map<String, EngineContext> configuredEngines;

  @PostConstruct
  public void init() {
    this.configuredEngines = new HashMap<>();
    for (Map.Entry<String, EngineConfiguration> config : configurationService.getConfiguredEngines().entrySet()) {
      EngineContext engineContext = constructEngineContext(config);
      configuredEngines.put(engineContext.getEngineAlias(), engineContext);
    }
  }

  @PreDestroy
  public void close() {
    if (this.configuredEngines != null) {
      this.configuredEngines.values().forEach(EngineContext::close);
      this.configuredEngines = null;
    }
  }

  public EngineContext getConfiguredEngineByAlias(final String engineAlias) {
    return configuredEngines.get(engineAlias);
  }

  public Collection<EngineContext> getConfiguredEngines() {
    return configuredEngines.values();
  }

  private EngineContext constructEngineContext(Map.Entry<String, EngineConfiguration> config) {
    return new EngineContext(config.getKey(), constructClient(config), configurationService);
  }

  private Client constructClient(Map.Entry<String, EngineConfiguration> config) {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, configurationService.getEngineConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, configurationService.getEngineReadTimeout());
    client.register(new LoggingFilter());
    if (config.getValue().getAuthentication().isEnabled()) {
      client.register(
        new BasicAccessAuthenticationFilter(
          configurationService.getDefaultEngineAuthenticationUser(config.getKey()),
          configurationService.getDefaultEngineAuthenticationPassword(config.getKey())
        )
      );
    }
    for (EngineRestFilter engineRestFilter : engineRestFilterProvider.getPlugins()) {
      client.register((ClientRequestFilter) requestContext -> engineRestFilter.filter(
        requestContext, config.getKey(), config.getValue().getName()
      ));
    }
    client.register(engineObjectMapperContextResolver);
    return client;
  }

  private static class LoggingFilter implements ClientRequestFilter {
    @Override
    public void filter(ClientRequestContext requestContext) {
      if (log.isTraceEnabled()) {
        Object body = requestContext.getEntity() != null ? requestContext.getEntity() : "";
        log.trace("sending request to [{}] with body [{}]", requestContext.getUri(), body);
      }
    }
  }

}
