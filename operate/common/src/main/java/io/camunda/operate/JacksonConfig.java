/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class JacksonConfig {

  private final OperateDateTimeFormatter dateTimeFormatter;

  @Autowired
  public JacksonConfig(final OperateDateTimeFormatter dateTimeFormatter) {
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Bean("operateObjectMapper")
  public ObjectMapper objectMapper() {
    final var connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setDateFormat(dateTimeFormatter.getGeneralDateTimeFormatString());
    final var delegate = new JacksonConfiguration(connectConfiguration);
    return delegate.createObjectMapper();
  }

  // Some common components autowire the datetime formatter directly. To avoid potentially impacting
  // critical code or needing to refactor in multiple places, expose the general date time formatter
  // as
  // a bean just like it was before the introduction of the OperateDateTimeFormatter component
  @Bean
  public DateTimeFormatter dateTimeFormatter(final OperateDateTimeFormatter dateTimeFormatter) {
    return dateTimeFormatter.getGeneralDateTimeFormatter();
  }

  @Bean
  public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    return new MappingJackson2HttpMessageConverter(objectMapper);
  }
}
