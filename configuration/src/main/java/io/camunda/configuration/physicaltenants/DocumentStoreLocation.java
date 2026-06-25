/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.LocalStore;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Location-identity tuple {@code (provider, coordinates)} used to detect whether two physical
 * tenants would share the same backing storage. In-memory stores are excluded — ephemeral and
 * process-local, they cannot collide.
 */
@NullMarked
record DocumentStoreLocation(String provider, List<String> coordinates) {

  /**
   * Creates a location for an AWS S3 store. Region is intentionally excluded because S3 bucket
   * names are globally unique across regions.
   */
  static DocumentStoreLocation aws(final AwsStore store) {
    return new DocumentStoreLocation(
        "aws", List.of(normalize(store.getBucketName()), normalize(store.getBucketPath())));
  }

  static DocumentStoreLocation gcp(final GcpStore store) {
    return new DocumentStoreLocation(
        "gcp", List.of(normalize(store.getBucketName()), normalize(store.getPrefix())));
  }

  /**
   * Creates a location for an Azure Blob Storage store. Connection string is excluded because it
   * may contain credentials; endpoint + container name + container path unambiguously identify the
   * location.
   */
  static DocumentStoreLocation azure(final AzureStore store) {
    return new DocumentStoreLocation(
        "azure",
        List.of(
            normalize(store.getContainerName()),
            normalize(store.getContainerPath()),
            normalize(store.getEndpoint())));
  }

  static DocumentStoreLocation local(final LocalStore store) {
    return new DocumentStoreLocation("local", List.of(normalize(store.getPath())));
  }

  String describe() {
    return String.format("provider=%s, coordinates=%s", provider, coordinates);
  }

  /**
   * Normalizes a store coordinate: null → {@code ""}, trimmed, lowercased, trailing slashes
   * stripped.
   */
  private static String normalize(final @Nullable String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim().toLowerCase();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
