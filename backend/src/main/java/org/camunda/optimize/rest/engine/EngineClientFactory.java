package org.camunda.optimize.rest.engine;

import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.camunda.optimize.service.util.Factory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.util.HashMap;
import java.util.Map;

public class EngineClientFactory implements Factory<Client, String> {

  protected Map<String,Client> instances = new HashMap<>();

  @Autowired
  protected OptimizeObjectMapperProvider optimizeObjectMapperProvider;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected BasicAccessAuthenticationFilterFactory basicAccessAuthenticationFilterFactory;

  protected Client newClient(String engineAlias) {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, configurationService.getEngineConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT,    configurationService.getEngineReadTimeout());
    if(configurationService.isEngineAuthenticationEnabled(engineAlias)) {
      client.register(basicAccessAuthenticationFilterFactory.getInstance(engineAlias));
    }
    client.register(optimizeObjectMapperProvider);
    return client;
  }

  @Override
  public Client getInstance(String engineAlias) {
    if (!instances.containsKey(engineAlias)) {
      instances.put(engineAlias,newClient(engineAlias));
    }
    return instances.get(engineAlias);
  }
}
