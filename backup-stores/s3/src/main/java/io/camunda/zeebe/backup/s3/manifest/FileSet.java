/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a {@link io.camunda.zeebe.backup.api.NamedFileSet} with attached metadata. It is
 * represented as a JSON object. Keys are file names, values are {@link FileMetadata metadata}.
 */
public record FileSet(Map<String, FileMetadata> files) {

  /**
   * Constructs a {@link FileSet} based on a list of files names. Assumes that none of the files
   * have any metadata attached.
   */
  public static FileSet withoutMetadata(final Set<String> fileNames) {
    final var savedFiles =
        fileNames.stream()
            .collect(Collectors.toMap(String.class::cast, ignored -> FileMetadata.none()));
    return new FileSet(savedFiles);
  }

  public static FileSet empty() {
    return new FileSet(Map.of());
  }

  public Set<String> names() {
    return files.keySet();
  }

  @JsonInclude(Include.NON_EMPTY)
  public record FileMetadata() {
    public static FileMetadata none() {
      return new FileMetadata();
    }
  }
}
