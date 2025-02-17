/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.index;

import java.util.List;

public record IndexAliasRequest(List<String> index, List<String> name) {

  public static IndexAliasRequest withName(final List<String> names) {
    return new IndexAliasRequest(List.of(), names);
  }

  public static IndexAliasRequest withIndex(final List<String> names) {
    return new IndexAliasRequest(names, List.of());
  }
}
