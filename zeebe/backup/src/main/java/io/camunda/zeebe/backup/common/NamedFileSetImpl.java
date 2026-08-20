/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.NamedFileSet;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public record NamedFileSetImpl(Map<String, Path> namedFiles) implements NamedFileSet {

  public NamedFileSetImpl(Map<String, Path> namedFiles) {
    this.namedFiles = Map.copyOf(namedFiles);
  }

  @Override
  public Set<String> names() {
    return namedFiles.keySet();
  }

  @Override
  public Set<Path> files() {
    return Set.copyOf(namedFiles.values());
  }
}
