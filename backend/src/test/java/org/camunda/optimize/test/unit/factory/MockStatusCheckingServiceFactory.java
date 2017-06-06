package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.service.status.StatusCheckingService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

public class MockStatusCheckingServiceFactory implements FactoryBean<StatusCheckingService> {

  @Override
  public StatusCheckingService getObject() throws Exception {
    return Mockito.mock(StatusCheckingService.class);
  }

  @Override
  public Class<?> getObjectType() {
    return StatusCheckingService.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
