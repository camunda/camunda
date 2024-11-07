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

public class GcpDocumentStoreProvider implements DocumentStoreProvider {

  private static final String BUCKET_NAME_PROPERTY = "BUCKET";

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
    return new GcpDocumentStore(bucketName);
  }
}
