package org.camunda.optimize.rest.engine;

import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

@Component
public class EngineClientFactory implements FactoryBean<Client>, ConfigurationReloadable {

  @Autowired
  protected OptimizeObjectMapperProvider optimizeObjectMapperProvider;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected BasicAccessAuthenticationFilterFactory basicAccessAuthenticationFilterFactory;


  protected Client newInstance() {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, configurationService.getEngineConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, configurationService.getEngineReadTimeout());
    String engineAlias = configurationService.getConfiguredEngines().keySet().iterator().next();
    if (configurationService.isEngineAuthenticationEnabled(engineAlias)) {
      client.register(basicAccessAuthenticationFilterFactory.getInstance(engineAlias));
    }
    client.register(optimizeObjectMapperProvider);
    return client;
  }

  private Client engineClient;

  @Override
  public Client getObject() throws Exception {
    if (engineClient == null) {
      engineClient = newInstance();
    }
    return engineClient;
  }

  @Override
  public Class<?> getObjectType() {
    return Client.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    engineClient = null;
  }
}
