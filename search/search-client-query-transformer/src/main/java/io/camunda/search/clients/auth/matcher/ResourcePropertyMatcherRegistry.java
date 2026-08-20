/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth.matcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for resource property matchers.
 *
 * <p>Provides lookup of matchers by resource class for property-based authorization checks.
 */
public class ResourcePropertyMatcherRegistry {

  private final Map<Class<?>, ResourcePropertyMatcher<?>> matchers = new HashMap<>();

  public ResourcePropertyMatcherRegistry() {
    register(new UserTaskPropertyMatcher());
  }

  public <T> void register(final ResourcePropertyMatcher<T> matcher) {
    matchers.put(matcher.getResourceClass(), matcher);
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<ResourcePropertyMatcher<T>> getMatcher(final T resource) {
    if (resource == null) {
      return Optional.empty();
    }
    return Optional.ofNullable((ResourcePropertyMatcher<T>) matchers.get(resource.getClass()));
  }
}
