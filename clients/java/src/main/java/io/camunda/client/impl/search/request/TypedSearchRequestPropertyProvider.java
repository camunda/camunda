/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.search.request;

public abstract class TypedSearchRequestPropertyProvider<T> {

  protected abstract T getSearchRequestProperty();

  public static <T> T provideSearchRequestProperty(final Object value) {
    if (value instanceof TypedSearchRequestPropertyProvider) {
      final TypedSearchRequestPropertyProvider<T> provider =
          (TypedSearchRequestPropertyProvider<T>) value;
      return provider.getSearchRequestProperty();
    }
    throw new UnsupportedOperationException(
        "Passed value is not of type " + TypedSearchRequestPropertyProvider.class);
  }
}
