package org.camunda.optimize.rest.engine;

import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.camunda.optimize.service.util.AbstractParametrizedFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.util.HashMap;
import java.util.Map;

@Component
public class EngineClientFactory extends AbstractParametrizedFactory<Client, String> {

  @Autowired
  protected OptimizeObjectMapperProvider optimizeObjectMapperProvider;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected BasicAccessAuthenticationFilterFactory basicAccessAuthenticationFilterFactory;


  @Override
  protected Client newInstance(String engineAlias) {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, configurationService.getEngineConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, configurationService.getEngineReadTimeout());
    if (configurationService.isEngineAuthenticationEnabled(engineAlias)) {
      client.register(basicAccessAuthenticationFilterFactory.getInstance(engineAlias));
    }
    client.register(optimizeObjectMapperProvider);
    return client;
  }

  /**
   * This method is used for testing purposes at the moment.
   */
  public void reloadConfiguration() {
    for (Map.Entry<String, Client> e : instances.entrySet()) {
      e.getValue().close();
    }
    instances = new HashMap<>();
  }

}
