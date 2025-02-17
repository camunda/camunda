/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import java.util.concurrent.ExecutorService;

public class AwsDocumentStoreFactory {
  public static AwsDocumentStore create(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final ExecutorService executor) {
    return new AwsDocumentStore(bucketName, defaultTTL, bucketPath, executor);
  }
}
