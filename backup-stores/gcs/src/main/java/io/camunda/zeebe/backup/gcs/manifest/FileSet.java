/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import io.camunda.zeebe.backup.api.NamedFileSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record FileSet(List<NamedFile> files) {

  public FileSet {
    Objects.requireNonNull(files);
    final var countByName =
        files.stream().collect(Collectors.groupingBy(NamedFile::name, Collectors.counting()));

    for (final var occurrence : countByName.entrySet()) {
      if (occurrence.getValue() > 1) {
        throw new IllegalArgumentException(
            "File name '%s' must be unique but occurred '%s' times in %s"
                .formatted(occurrence.getKey(), occurrence.getValue(), files));
      }
    }
  }

  static FileSet of(final NamedFileSet fileSet) {
    if (fileSet == null) {
      return new FileSet(List.of());
    }

    return new FileSet(fileSet.namedFiles().keySet().stream().map(NamedFile::new).toList());
  }

  public record NamedFile(String name) {
    public NamedFile {
      Objects.requireNonNull(name);
    }
  }
}
