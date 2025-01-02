/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsDocumentStoreProvider implements DocumentStoreProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AwsDocumentStoreProvider.class);
  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[\\u0000-\\u001F\\\\]");

  private static final String BUCKET_NAME_PROPERTY = "BUCKET";
  private static final String BUCKET_TTL = "BUCKET_TTL";
  private static final String BUCKET_PATH = "BUCKET_PATH";

  @Override
  public DocumentStore createDocumentStore(
      final DocumentStoreConfigurationRecord configuration, final ExecutorService executorService) {
    final String bucketName =
        Optional.ofNullable(configuration.properties().get(BUCKET_NAME_PROPERTY))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Failed to configure document store with id '"
                            + configuration.id()
                            + "': missing required property '"
                            + BUCKET_NAME_PROPERTY
                            + "'"));

    return AwsDocumentStoreFactory.create(
        bucketName, getDefaultTTL(configuration), getBucketPath(configuration), executorService);
  }

  private static Long getDefaultTTL(final DocumentStoreConfigurationRecord configuration) {
    final String bucketTTL = configuration.properties().get(BUCKET_TTL);

    if (bucketTTL == null) {
      LOG.warn("AWS {} property is not set", BUCKET_TTL);
      return null;
    }

    try {
      return Long.valueOf(bucketTTL);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + BUCKET_TTL
              + " must be a number'");
    }
  }

  private static String getBucketPath(final DocumentStoreConfigurationRecord configuration) {
    String bucketPath = Objects.requireNonNullElse(configuration.properties().get(BUCKET_PATH), "");

    if (INVALID_CHARACTERS.matcher(bucketPath).find()) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + BUCKET_PATH
              + " is invalid. Must not contain \\ character'");
    }

    if (!bucketPath.isEmpty() && !bucketPath.endsWith("/")) {
      bucketPath = bucketPath + "/";
    }

    return bucketPath;
  }
}
