/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.gcp;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class GcpDocumentStoreProvider implements DocumentStoreProvider {

  private static final String BUCKET_NAME_PROPERTY = "BUCKET";
  private static final String PREFIX_PROPERTY = "PREFIX";

  private static final String DEFAULT_PREFIX = "temp/";

  @Override
  public DocumentStore createDocumentStore(
      final DocumentStoreConfigurationRecord configuration, final ExecutorService executorService) {
    return new GcpDocumentStore(
        getBucketNameProperty(configuration), getPrefixProperty(configuration), executorService);
  }

  private String getBucketNameProperty(final DocumentStoreConfigurationRecord configuration) {
    return Optional.ofNullable(configuration.properties().get(BUCKET_NAME_PROPERTY))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Failed to configure document store with id '"
                        + configuration.id()
                        + "': missing required property '"
                        + BUCKET_NAME_PROPERTY
                        + "'"));
  }

  private String getPrefixProperty(final DocumentStoreConfigurationRecord configuration) {
    return Optional.ofNullable(configuration.properties().get(PREFIX_PROPERTY))
        .orElse(DEFAULT_PREFIX);
  }
}
