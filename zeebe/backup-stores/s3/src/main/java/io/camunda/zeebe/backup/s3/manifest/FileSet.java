/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a {@link io.camunda.zeebe.backup.api.NamedFileSet} with attached metadata. It is
 * represented as a JSON object. Keys are file names, values are {@link FileMetadata metadata}.
 */
@JsonDeserialize(using = FileSet.Deserializer.class)
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
  public record FileMetadata(Optional<String> compressionAlgorithm) {
    public static FileMetadata withCompression(final String algorithm) {
      return new FileMetadata(Optional.of(algorithm));
    }

    public static FileMetadata none() {
      return new FileMetadata(Optional.empty());
    }
  }

  /**
   * Custom deserializer that supports reading {@link FileSet} from the canonical representation as
   * a map of string to {@link FileMetadata} while also supporting reading from a plain array of
   * fileNames which was the serialization format in 8.1
   */
  static final class Deserializer extends JsonDeserializer<FileSet> {
    private static final TypeReference<Map<String, FileMetadata>> WITH_METADATA =
        new TypeReference<>() {};
    private static final TypeReference<Set<String>> WITHOUT_METADATA = new TypeReference<>() {};

    @Override
    public FileSet deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final var codec = p.getCodec();
      if (p.currentToken() == JsonToken.START_ARRAY) {
        final var content = codec.readValue(p, WITHOUT_METADATA);
        return FileSet.withoutMetadata(content);
      } else if (p.currentToken() == JsonToken.START_OBJECT) {
        try (final var parser = codec.readTree(p).get("files").traverse(codec)) {
          final Map<String, FileMetadata> content = parser.readValueAs(WITH_METADATA);
          return new FileSet(content);
        }
      }
      ctxt.handleUnexpectedToken(FileSet.class, p);
      return null; // we never reach this, `handleUnexpectedToken` will throw for us.
    }
  }
}
