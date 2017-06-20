package org.camunda.optimize.test.it.factory;

import org.camunda.optimize.service.es.TransportClientFactory;
import org.elasticsearch.client.Client;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

public class SpyTransportClientFactory implements FactoryBean<Client> {

  private Client spyedInstance;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public Client getObject() throws Exception {
    if (spyedInstance == null) {
      AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
      TransportClientFactory transportClientFactory = beanFactory.createBean(TransportClientFactory.class);
      spyedInstance = Mockito.spy(transportClientFactory.getObject());
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
