/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import java.lang.reflect.Type;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

public class PackageSpecificJackson2HttpMessageConverter
    extends MappingJackson2HttpMessageConverter {
  private final String basePackage;

  public PackageSpecificJackson2HttpMessageConverter(final String basePackage) {
    this.basePackage = basePackage;
  }

  @Override
  public boolean canRead(final Type type, final Class<?> contextClass, final MediaType mediaType) {
    if (type.getTypeName().startsWith(basePackage)) {
      return super.canRead(type, contextClass, mediaType);
    }
    return false;
  }

  @Override
  public boolean canWrite(final Class<?> clazz, final MediaType mediaType) {
    if (clazz.getName().startsWith(basePackage)) {
      return super.canWrite(clazz, mediaType);
    }
    return false;
  }
}
