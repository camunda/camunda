package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.es.TransportClientFactory;
import org.elasticsearch.client.transport.TransportClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

public class SpyTransportClientFactory implements FactoryBean<TransportClient> {

  private TransportClient spyedInstance;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public TransportClient getObject() throws Exception {
    if (spyedInstance == null) {
      AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
      TransportClientFactory transportClientFactory = beanFactory.createBean(TransportClientFactory.class);
      spyedInstance = Mockito.spy(transportClientFactory.getObject());
    }
    return spyedInstance;
  }

  @Override
  public Class<?> getObjectType() {
    return TransportClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
