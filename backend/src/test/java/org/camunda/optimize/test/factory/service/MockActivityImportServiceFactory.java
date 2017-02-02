package org.camunda.optimize.test.factory.service;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

public class MockActivityImportServiceFactory implements FactoryBean<ActivityImportService> {

  @Override
  public ActivityImportService getObject() throws Exception {
    return Mockito.mock(ActivityImportService.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ActivityImportService.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
