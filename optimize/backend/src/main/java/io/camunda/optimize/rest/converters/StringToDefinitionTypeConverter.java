/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.converters;

import io.camunda.optimize.dto.optimize.DefinitionType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToDefinitionTypeConverter implements Converter<String, DefinitionType> {
  @Override
  public DefinitionType convert(final String source) {
    return DefinitionType.valueOf(source.toUpperCase());
  }
}
