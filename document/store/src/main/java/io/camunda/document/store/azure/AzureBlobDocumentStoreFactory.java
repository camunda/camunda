/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.util.concurrent.ExecutorService;

public class AzureBlobDocumentStoreFactory {

  public static AzureBlobDocumentStore createWithDefaultCredential(
      final String endpoint,
      final String containerName,
      final String containerPath,
      final ExecutorService executor) {
    final BlobServiceClient serviceClient =
        new BlobServiceClientBuilder()
            .endpoint(endpoint)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    final BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
    return new AzureBlobDocumentStore(
        containerName, containerPath, containerClient, serviceClient, executor);
  }

  public static AzureBlobDocumentStore createWithConnectionString(
      final String connectionString,
      final String containerName,
      final String containerPath,
      final ExecutorService executor) {
    final BlobServiceClient serviceClient =
        new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
    final BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
    return new AzureBlobDocumentStore(
        containerName, containerPath, containerClient, serviceClient, executor);
  }
}
