/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.NamedFileSet;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NamedFileSetImpl implements NamedFileSet {

  private final Map<String, Path> namedFiles = new HashMap<>();

  @Override
  public Set<String> names() {
    return Set.copyOf(namedFiles.keySet());
  }

  @Override
  public Set<Path> files() {
    return Set.copyOf(namedFiles.values());
  }

  @Override
  public Map<String, Path> namedFiles() {
    return namedFiles;
  }

  public void addFile(final String name, final Path file) {
    namedFiles.put(name, file);
  }
}
