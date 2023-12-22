/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure.shared;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.specialized.BlobLeaseClient;
import com.azure.storage.blob.specialized.BlobLeaseClientBuilder;
import com.azure.storage.common.implementation.Constants;

public class BlobRequestsOptionsManagement {
  public static BlobRequestConditions acquireBlobLease(final BlobClient blobClient) {
    final BlobLeaseClient leaseClient =
        new BlobLeaseClientBuilder().blobClient(blobClient).buildClient();
    try {
      return new BlobRequestConditions().setLeaseId(leaseClient.acquireLease(-1));
    } catch (final Exception e) {
      // Blob client might not exist
      return new BlobRequestConditions();
    }
  }

  public static void releaseLease(final BlobClient blobClient) {
    final BlobLeaseClient leaseClient =
        new BlobLeaseClientBuilder().blobClient(blobClient).buildClient();
    try {
      leaseClient.releaseLease();
    } catch (final Exception e) {
      // Blob client might not exist
    }
  }

  public static void disableOverwrite(final BlobRequestConditions blobRequestConditions) {
    // Optionally limit requests to resources that do not match the passed ETag.
    // None will match therefore it will not overwrite.
    blobRequestConditions.setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);
  }
}
