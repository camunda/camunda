package org.camunda.optimize.rest.engine;

import org.camunda.optimize.service.util.ConfigurationService;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class EngineClientFactory implements FactoryBean<Client> {

  private Client instance;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private BasicAccessAuthenticationFilter basicAccessAuthenticationFilter;

  @Override
  public Client getObject() throws Exception {
    if (instance == null) {
        instance = newClient();
    }
    return instance;
  }

  private Client newClient() {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, configurationService.getEngineConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT,    configurationService.getEngineReadTimeout());
    if(configurationService.isEngineAuthenticationEnabled()) {
      client.register(basicAccessAuthenticationFilter);
    }
    return client;
  }

  @Override
  public Class<?> getObjectType() {
    return Client.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
