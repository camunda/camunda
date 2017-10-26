package org.camunda.optimize.service.util;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class DateTimeFormatterFactory implements FactoryBean<DateTimeFormatter> {

  @Autowired
  private ConfigurationService configurationService;

  private DateTimeFormatter dateTimeFormatter;

  @Override
  public DateTimeFormatter getObject() throws Exception {
    if (dateTimeFormatter == null) {
      dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getDateFormat());
    }
    return dateTimeFormatter;
  }

  @Override
  public Class<?> getObjectType() {
    return DateTimeFormatter.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
