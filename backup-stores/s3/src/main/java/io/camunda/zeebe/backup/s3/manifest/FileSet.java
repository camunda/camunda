/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3.manifest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a {@link io.camunda.zeebe.backup.api.NamedFileSet} with attached metadata. It is
 * represented as a JSON object. Keys are file names, values are {@link FileMetadata metadata}.
 */
@JsonDeserialize(using = FileSetDeserializer.class)
public record FileSet(@JsonAnySetter @JsonAnyGetter Map<String, FileMetadata> savedFiles) {

  /**
   * Constructs a {@link FileSet} based on a list of files names. Assumes that none of the saved
   * files have any metadata attached.
   */
  public static FileSet withoutMetadata(final Set<String> savedFilesWithoutMetadata) {
    final var savedFiles =
        savedFilesWithoutMetadata.stream()
            .collect(
                Collectors.toMap(
                    String.class::cast, (ignored) -> new FileMetadata(Optional.empty())));
    return new FileSet(savedFiles);
  }

  public static FileSet empty() {
    return new FileSet(Map.of());
  }

  public Set<Entry<String, FileMetadata>> entries() {
    return savedFiles.entrySet();
  }

  public Set<String> names() {
    return savedFiles.keySet();
  }

  @JsonInclude(Include.NON_EMPTY)
  public record FileMetadata(Optional<String> compressionAlgorithm) {
    public static FileMetadata withCompression(final String compressionAlgorithm) {
      return new FileMetadata(Optional.of(compressionAlgorithm));
    }

    public static FileMetadata withoutCompression() {
      return new FileMetadata(Optional.empty());
    }
  }
}
