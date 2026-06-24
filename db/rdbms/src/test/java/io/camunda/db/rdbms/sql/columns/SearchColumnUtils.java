/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.platform.commons.support.ReflectionSupport;

public class SearchColumnUtils {
  public static Set<SearchColumn> findAll() {
    return ReflectionSupport.findAllClassesInPackage(
            "io.camunda.db.rdbms.sql.columns",
            c -> {
              final var interfaces = c.getInterfaces();
              return Arrays.asList(interfaces).contains(SearchColumn.class);
            },
            ignored -> true)
        .stream()
        .flatMap(
            clazz -> {
              final Object[] enumConstants = clazz.getEnumConstants();
              return Arrays.stream(enumConstants).map(e -> (SearchColumn<?>) e);
            })
        .collect(Collectors.toSet());
  }
}
