/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.oauth.PersistedOAuth2AuthorizedClientDeletionTask;
import io.camunda.authentication.session.ConditionalOnPersistentWebSessionEnabled;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClientImpl;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.index.PersistentAuthorizedClientIndexDescriptor;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnPersistentWebSessionEnabled
public class PersistedOAuth2AuthorizedClientConfiguration {
  private final ConnectConfiguration connectConfiguration;

  public PersistedOAuth2AuthorizedClientConfiguration(
      final ConnectConfiguration connectConfiguration) {
    this.connectConfiguration = connectConfiguration;
  }

  @Bean
  public PersistentAuthorizedClientIndexDescriptor persistentAuthorizedClientIndex() {
    final var indexPrefix = connectConfiguration.getIndexPrefix();
    final var isElasticsearch =
        ConnectionTypes.from(connectConfiguration.getType()).equals(ConnectionTypes.ELASTICSEARCH);
    return new PersistentAuthorizedClientIndexDescriptor(indexPrefix, isElasticsearch);
  }

  @Bean
  public PersistentOAuth2AuthorizedClientsClient authorizedClientsClient(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentAuthorizedClientIndexDescriptor descriptor) {
    return new PersistentOAuth2AuthorizedClientsClientImpl(searchClient, writeClient, descriptor);
  }

  @Bean("persistentOAuth2AuthorizedClientsDeletionTaskExecutor")
  public ScheduledThreadPoolExecutor
      persistentPersistentOAuth2AuthorizedClientsDeletionTaskExecutor(
          final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient) {
    final var executor = createTaskExecutor();
    executor.schedule(
        new PersistedOAuth2AuthorizedClientConfiguration.SelfSchedulingTask(
            executor,
            new PersistedOAuth2AuthorizedClientDeletionTask(authorizedClientsClient),
            PersistedOAuth2AuthorizedClientDeletionTask.DELETE_EXPIRED_AUTHORIZED_CLIENTS_DELAY),
        PersistedOAuth2AuthorizedClientDeletionTask.DELETE_EXPIRED_AUTHORIZED_CLIENTS_DELAY,
        TimeUnit.MILLISECONDS);
    return executor;
  }

  public ScheduledThreadPoolExecutor createTaskExecutor() {
    final var threadFactory =
        Thread.ofPlatform()
            .name("camunda-authorized-clients-deletion-", 0)
            .uncaughtExceptionHandler(
                FatalErrorHandler.uncaughtExceptionHandler(
                    PersistentOAuth2AuthorizedClientsClientImpl.LOG))
            .factory();
    final var executor = new ScheduledThreadPoolExecutor(0, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    executor.allowCoreThreadTimeOut(true);
    executor.setKeepAliveTime(1, TimeUnit.MINUTES);
    executor.setCorePoolSize(1);
    return executor;
  }

  static final class SelfSchedulingTask implements Runnable {

    private final ScheduledThreadPoolExecutor executor;
    private final Runnable task;
    private final long delay;

    SelfSchedulingTask(
        final ScheduledThreadPoolExecutor executor, final Runnable task, final long delay) {
      this.executor = executor;
      this.task = task;
      this.delay = delay;
    }

    @Override
    public void run() {
      task.run();
      executor.schedule(this, delay, TimeUnit.MILLISECONDS);
    }
  }
}
