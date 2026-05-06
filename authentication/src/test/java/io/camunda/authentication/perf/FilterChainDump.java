/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.perf;

import jakarta.servlet.Filter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.web.FilterChainProxy;

/**
 * Helper that prints every {@link org.springframework.security.web.SecurityFilterChain} bound to a
 * {@link FilterChainProxy}, with shallow reflective introspection of authentication-related filters
 * so the {@code AuthenticationManager} / {@code AuthenticationConverter} / {@code
 * BearerTokenResolver} wiring is visible.
 */
final class FilterChainDump {

  /**
   * Field names whose values are interesting collaborators of an authentication filter. Searched
   * across the full class hierarchy of each filter; absent fields are silently skipped.
   */
  private static final Set<String> INTERESTING_FIELDS =
      Set.of(
          "authenticationManager",
          "authenticationManagerResolver",
          "authenticationConverter",
          "requestMatcher",
          "bearerTokenResolver",
          "authenticationEntryPoint",
          "successHandler",
          "failureHandler",
          "securityContextRepository",
          "securityContextHolderStrategy");

  /**
   * Class-name fragments that mark a filter as worth introspecting. Anything outside this list is
   * printed as just its class name.
   */
  private static final List<String> INTERESTING_FILTER_FRAGMENTS =
      List.of(
          "AuthenticationFilter",
          "BearerToken",
          "OAuth2",
          "SessionManagement",
          "Authorization",
          "SecurityContext");

  private FilterChainDump() {}

  static void dump(final String label, final FilterChainProxy proxy) {
    final var chains = proxy.getFilterChains();
    System.out.println();
    System.out.println("=================================================================");
    System.out.println(" Filter chain dump — " + label);
    System.out.println(" total chains: " + chains.size());
    System.out.println("=================================================================");
    for (int i = 0; i < chains.size(); i++) {
      final var chain = chains.get(i);
      System.out.printf("%n[chain #%d] %s%n", i, chain);
      final List<Filter> filters = chain.getFilters();
      System.out.printf("  filters (%d):%n", filters.size());
      for (int j = 0; j < filters.size(); j++) {
        final Filter filter = filters.get(j);
        final String filterClass = filter.getClass().getName();
        System.out.printf("    %2d. %s%n", j, filterClass);
        if (isInteresting(filterClass)) {
          introspect(filter, "          ");
        }
      }
    }
    System.out.println();
  }

  private static boolean isInteresting(final String filterClass) {
    return INTERESTING_FILTER_FRAGMENTS.stream().anyMatch(filterClass::contains);
  }

  /**
   * Walk every non-static field declared anywhere in the filter's class hierarchy whose name
   * matches {@link #INTERESTING_FIELDS}, print its field name + the runtime class of the value. For
   * wrapper-style values (an {@code Observation*} that delegates to another instance), unwrap one
   * level.
   */
  private static void introspect(final Object target, final String indent) {
    final Map<String, Object> found = new LinkedHashMap<>();
    Class<?> cls = target.getClass();
    while (cls != null && cls != Object.class) {
      for (final Field field : cls.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if (!INTERESTING_FIELDS.contains(field.getName())) {
          continue;
        }
        if (found.containsKey(field.getName())) {
          continue; // first wins (subclass shadows superclass)
        }
        try {
          field.setAccessible(true);
          final Object value = field.get(target);
          found.put(field.getName(), value);
        } catch (final IllegalAccessException ignored) {
          // skip — not a fatal error for diagnostic output
        }
      }
      cls = cls.getSuperclass();
    }
    if (found.isEmpty()) {
      return;
    }
    for (final var entry : found.entrySet()) {
      final Object value = entry.getValue();
      System.out.printf(
          "%s%s = %s%n",
          indent, entry.getKey(), value == null ? "null" : value.getClass().getName());
      if (value != null) {
        unwrapIfDelegates(value, indent + "    -> ");
      }
    }
  }

  /**
   * If {@code value} carries a {@code delegate} or {@code target} field that points at another
   * non-trivial object, print that one level deep. Catches the common Observation/Decorator wrapper
   * pattern (e.g. {@code ObservationAuthenticationManager} delegating to a {@code
   * ProviderManager}).
   */
  private static void unwrapIfDelegates(final Object value, final String indent) {
    Class<?> cls = value.getClass();
    while (cls != null && cls != Object.class) {
      for (final Field field : cls.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        final String name = field.getName();
        if (!name.equals("delegate") && !name.equals("target")) {
          continue;
        }
        try {
          field.setAccessible(true);
          final Object inner = field.get(value);
          if (inner == null) {
            return;
          }
          System.out.printf("%s%s = %s%n", indent, name, inner.getClass().getName());
        } catch (final IllegalAccessException ignored) {
          // skip
        }
      }
      cls = cls.getSuperclass();
    }
  }
}
