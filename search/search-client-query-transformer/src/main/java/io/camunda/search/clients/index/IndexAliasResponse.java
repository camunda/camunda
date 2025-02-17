/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.index;

import java.util.Map;

public record IndexAliasResponse(Map<String, IndexAliases> indices) {

  public record IndexAliases(Map<String, AliasDefinition> aliases) {}

  public record AliasDefinition(
      String indexRouting, Boolean isWriteIndex, String routing, String searchRouting) {}
}
