/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3.manifest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.camunda.zeebe.backup.s3.manifest.FileSet.FileMetadata;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Custom deserializer for {@link FileSet}. In addition to the usual serialization format, it also
 * supports deserializing from a plain list of file names and assumes that these files have no
 * metadata attached. This is necessary to read manifests created by previous versions.
 */
public class FileSetDeserializer extends JsonDeserializer<FileSet> {

  @Override
  public FileSet deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    final var token = p.currentToken();
    final var codec = p.getCodec();
    if (token == JsonToken.START_OBJECT) {
      final var savedFiles = codec.readValue(p, new TypeReference<Map<String, FileMetadata>>() {});
      return new FileSet(savedFiles);
    } else if (token == JsonToken.START_ARRAY) {
      final var entries = codec.readValue(p, new TypeReference<Set<String>>() {});
      return FileSet.withoutMetadata(entries);
    } else {
      throw new IOException("SavedFileSet could not be parsed");
    }
  }
}
