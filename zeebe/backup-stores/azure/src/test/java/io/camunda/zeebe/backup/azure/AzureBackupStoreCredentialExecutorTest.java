/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpPipeline;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceVersion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class AzureBackupStoreCredentialExecutorTest {

  @AutoClose(method = "shutdownNow")
  private final ExecutorService executor = AzureBackupStore.newCredentialExecutor();

  @Test
  void shouldRunTasksOnDaemonPlatformThreads() {
    // given
    final var thread = new CompletableFuture<Thread>();

    // when
    executor.execute(() -> thread.complete(Thread.currentThread()));

    // then
    assertThat(thread)
        .succeedsWithin(10, TimeUnit.SECONDS)
        .satisfies(
            t -> {
              assertThat(t.isVirtual()).isFalse();
              assertThat(t.isDaemon()).isTrue();
              assertThat(t.getName()).startsWith("zeebe-backup-azure-credential-");
            });
  }

  @Test
  void shouldRunMultipleTasksConcurrently() {
    // given
    final var concurrentTasks = 4;
    final var allStarted = new CountDownLatch(concurrentTasks);
    final var futures = new CompletableFuture<?>[concurrentTasks];

    // when
    for (int i = 0; i < concurrentTasks; i++) {
      futures[i] =
          CompletableFuture.runAsync(
              () -> {
                allStarted.countDown();
                try {
                  if (!allStarted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tasks did not run concurrently");
                  }
                } catch (final InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
              },
              executor);
    }

    // then
    assertThat(CompletableFuture.allOf(futures)).succeedsWithin(15, TimeUnit.SECONDS);
  }

  @Test
  void shouldShutDownCredentialExecutorWhenStoreIsClosed() {
    // given
    final var config =
        new AzureBackupConfig.Builder()
            .withCreateContainer(false)
            .withContainerName("test")
            .build();
    final var client = mock(BlobServiceClient.class);
    final var containerClient = mock(BlobContainerClient.class);
    when(client.getBlobContainerClient(anyString())).thenReturn(containerClient);
    when(containerClient.exists()).thenReturn(true);
    when(client.getHttpPipeline()).thenReturn(mock(HttpPipeline.class));
    when(client.getAccountUrl()).thenReturn("https://test.blob.core.windows.net");
    when(client.getServiceVersion()).thenReturn(BlobServiceVersion.getLatest());
    final var store = new AzureBackupStore(config, client, executor);

    // when
    store.closeAsync().join();

    // then
    assertThat(executor.isShutdown()).isTrue();
  }
}
