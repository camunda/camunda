/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor;

import io.camunda.zeebe.gateway.Interceptor;
import java.lang.reflect.InvocationTargetException;

public class Descriptor {

  private final String id;
  private final Class<? extends Interceptor> interceptorClass;

  public Descriptor(final String id, final Class<? extends Interceptor> interceptorClass) {
    this.id = id;
    this.interceptorClass = interceptorClass;
  }

  public Interceptor newInstance() {
    try {
      return interceptorClass.getDeclaredConstructor().newInstance();
    } catch (final InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new RuntimeException("Unable to create a new instance of interceptor " + id, e);
    }
  }
}
