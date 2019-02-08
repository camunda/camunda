package org.camunda.optimize.test.it.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;


public class SpyTokenServiceFactory implements FactoryBean<SessionService> {
  private SessionService sessionService;

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private DefinitionAuthorizationService authorizationService;

  @Override
  public SessionService getObject() throws Exception {
    if (sessionService == null) {
      sessionService = new SessionService(configurationService, ImmutableList.of(authorizationService));
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
