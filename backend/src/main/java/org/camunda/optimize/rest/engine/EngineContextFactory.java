/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine;

import lombok.AllArgsConstructor;
import org.camunda.optimize.plugin.EngineRestFilterProvider;
import org.camunda.optimize.plugin.engine.rest.EngineRestFilter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.util.EngineVersionChecker.checkEngineVersionSupport;

@AllArgsConstructor
@Component
public class EngineContextFactory {
  private static final Logger logger = LoggerFactory.getLogger(EngineContextFactory.class);

  private final ConfigurationService configurationService;
  private final EngineObjectMapperContextResolver engineObjectMapperContextResolver;
  private final EngineRestFilterProvider engineRestFilterProvider;

  private Map<String, EngineContext> configuredEngines;

  @PostConstruct
  public void init() {
    this.configuredEngines = new HashMap<>();
    for (Map.Entry<String, EngineConfiguration> config : configurationService.getConfiguredEngines().entrySet()) {
      EngineContext engineContext = constructEngineContext(config);
      checkEngineVersionSupport(
        configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()),
        engineContext
      );
      configuredEngines.put(engineContext.getEngineAlias(), engineContext);
    }
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
      client.register(new ClientRequestFilter() {

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
          engineRestFilter.filter(requestContext, config.getKey(), config.getValue().getName());
        }

      });
    }
    client.register(engineObjectMapperContextResolver);
    return client;
  }

  public class LoggingFilter implements ClientRequestFilter {
    @Override
    public void filter(ClientRequestContext requestContext) {
      Object body = requestContext.getEntity() != null ? requestContext.getEntity() : "";
      logger.trace("sending request to [{}] with body [{}]", requestContext.getUri(), body);
    }
  }

  public EngineContext getConfiguredEngineByAlias(final String engineAlias) {
    return configuredEngines.get(engineAlias);
  }

  public Collection<EngineContext> getConfiguredEngines() {
    return configuredEngines.values();
  }

}
