package org.camunda.optimize.rest.engine;

import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class EngineContextFactory {

  private List<EngineContext> configuredEngines;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  protected OptimizeObjectMapperProvider optimizeObjectMapperProvider;

  @PostConstruct
  public void init() {
    configuredEngines = new ArrayList();
    for (Map.Entry<String, EngineConfiguration> config : configurationService.getConfiguredEngines().entrySet()) {
      configuredEngines.add(constructEngineContext(config));
    }
  }

  private EngineContext constructEngineContext(Map.Entry<String, EngineConfiguration> config) {
    EngineContext result = new EngineContext();
    result.setEngineClient(constructClient(config));
    result.setEngineAlias(config.getKey());
    return result;
  }

  private Client constructClient(Map.Entry<String, EngineConfiguration> config) {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, configurationService.getEngineConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, configurationService.getEngineReadTimeout());
    if (config.getValue().getAuthentication().isEnabled()) {
      client.register(
          new BasicAccessAuthenticationFilter(
            configurationService.getDefaultEngineAuthenticationUser(config.getKey()),
            configurationService.getDefaultEngineAuthenticationPassword(config.getKey())
          )
      );
    }
    client.register(optimizeObjectMapperProvider);
    return client;
  }

  public List<EngineContext> getConfiguredEngines() {
    return configuredEngines;
  }

}
