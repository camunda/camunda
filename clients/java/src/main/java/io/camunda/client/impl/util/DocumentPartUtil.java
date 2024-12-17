/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.util;

import io.camunda.zeebe.client.protocol.rest.DocumentMetadata;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DocumentPartUtil {

  public static String getFilenameOrDefault(
      @Nonnull final DocumentMetadata metadata, @Nullable final String userDefinedDocumentId) {
    final String documentIdForFilename =
        Optional.ofNullable(userDefinedDocumentId).orElse(UUID.randomUUID().toString());
    return Optional.ofNullable(metadata.getFileName()).orElse("document-" + documentIdForFilename);
  }
}
