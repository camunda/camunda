package org.camunda.optimize.test.it.factory;

import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;


public class SpyTokenServiceFactory implements FactoryBean<SessionService> {
  private SessionService sessionService;

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public SessionService getObject() throws Exception {
    if (sessionService == null) {
      sessionService = new SessionService();
      sessionService.setConfigurationService(configurationService);
      sessionService = Mockito.spy(sessionService);
    }
    return sessionService;
  }

  @Override
  public Class<?> getObjectType() {
    return SessionService.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
