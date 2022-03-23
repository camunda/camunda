/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class OptimizeDateTimeFormatterFactory implements FactoryBean<DateTimeFormatter> {

  private DateTimeFormatter dateTimeFormatter;

  @Override
  public DateTimeFormatter getObject() {
    if (dateTimeFormatter == null) {
      dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
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
