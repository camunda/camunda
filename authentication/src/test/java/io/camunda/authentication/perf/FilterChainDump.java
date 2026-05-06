/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.perf;

import jakarta.servlet.Filter;
import java.util.List;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

/** Helper that prints every {@link SecurityFilterChain} bound to a {@link FilterChainProxy}. */
final class FilterChainDump {

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
        System.out.printf("    %2d. %s%n", j, filters.get(j).getClass().getName());
      }
    }
    System.out.println();
  }
}
