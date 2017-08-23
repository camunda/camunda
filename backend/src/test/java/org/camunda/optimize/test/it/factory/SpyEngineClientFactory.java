package org.camunda.optimize.test.it.factory;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.camunda.optimize.service.util.Factory;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Client;
import java.util.Map;

public class SpyEngineClientFactory extends EngineClientFactory {

  protected Client newClient(String engineAlias) {
    return Mockito.spy(super.newClient(engineAlias));
  }
}