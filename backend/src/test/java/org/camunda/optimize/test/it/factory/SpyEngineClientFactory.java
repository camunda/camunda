package org.camunda.optimize.test.it.factory;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Client;

public class SpyEngineClientFactory implements FactoryBean<Client> {
  private Client spyedInstance;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public Client getObject() throws Exception {
    if (spyedInstance == null) {
      AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
      EngineClientFactory engineClientFactory = beanFactory.createBean(EngineClientFactory.class);
      spyedInstance = Mockito.spy(engineClientFactory.getObject());
    }
    return spyedInstance;
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