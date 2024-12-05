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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsDocumentStoreProvider implements DocumentStoreProvider {

  private static final String BUCKET_NAME_PROPERTY = "BUCKET";
  private static final String BUCKET_TTL = "BUCKET_TTL";
  private static final Logger log = LoggerFactory.getLogger(AwsDocumentStoreProvider.class);

  @Override
  public DocumentStore createDocumentStore(final DocumentStoreConfigurationRecord configuration) {
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

    return AwsDocumentStoreFactory.create(bucketName, getDefaultTTL(configuration));
  }

  private static Long getDefaultTTL(final DocumentStoreConfigurationRecord configuration) {
    final String bucketTTL = configuration.properties().get(BUCKET_TTL);

    if (bucketTTL == null) {
      log.warn("AWS {} property is not set", BUCKET_TTL);
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
}
