package org.camunda.optimize.test.it.factory;

import org.elasticsearch.client.Client;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class MockTransportClientFactory implements FactoryBean<Client> {

  private Client spyedInstance;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public Client getObject() {
    return Mockito.mock(Client.class);
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
