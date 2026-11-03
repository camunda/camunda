/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Wraps a MyBatis mapper so that every call, read or write, has its runtime exceptions passed
 * through {@link DatabaseExceptionTranslator#translateIfNeeded(RuntimeException)}.
 */
public final class TranslatingMapperProxy implements InvocationHandler {

  private final Object delegate;

  private TranslatingMapperProxy(final Object delegate) {
    this.delegate = delegate;
  }

  public static <T> T wrap(final Class<T> mapperType, final T delegate) {
    return mapperType.cast(
        Proxy.newProxyInstance(
            mapperType.getClassLoader(),
            new Class<?>[] {mapperType},
            new TranslatingMapperProxy(delegate)));
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (final InvocationTargetException e) {
      final Throwable cause = e.getTargetException();
      if (cause instanceof final RuntimeException runtimeException) {
        throw DatabaseExceptionTranslator.translateIfNeeded(runtimeException);
      }
      throw cause;
    }
  }
}
