/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.session.ConditionalOnPersistentWebSessionEnabled;
import io.camunda.authentication.session.WebSessionDeletionTask;
import io.camunda.authentication.session.WebSessionMapper;
import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.authentication.session.WebSessionRepository;
import io.camunda.db.se.config.ConnectConfiguration;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PersistentWebSessionSearchImpl;
import io.camunda.webapps.schema.descriptors.usermanagement.index.PersistentWebSessionIndexDescriptor;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@Configuration
@EnableSpringHttpSession
@ConditionalOnPersistentWebSessionEnabled
@ConditionalOnRestGatewayEnabled
public class WebSessionRepositoryConfiguration {

  private final GenericConversionService conversionService;
  private final ConnectConfiguration connectConfiguration;

  public WebSessionRepositoryConfiguration(
      final GenericConversionService conversionService,
      final ConnectConfiguration connectConfiguration) {
    this.conversionService = conversionService;
    this.connectConfiguration = connectConfiguration;
  }

  @Bean
  public PersistentWebSessionIndexDescriptor persistentWebSessionIndex() {
    final var indexPrefix = connectConfiguration.getIndexPrefix();
    final var isElasticsearch =
        ConnectionTypes.from(connectConfiguration.getType()).equals(ConnectionTypes.ELASTICSEARCH);
    return new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch);
  }

  @Bean
  public PersistentWebSessionClient persistentWebSessionClient(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentWebSessionIndexDescriptor descriptor) {
    return new PersistentWebSessionSearchImpl(searchClient, writeClient, descriptor);
  }

  @Bean
  public WebSessionRepository webSessionRepository(
      final PersistentWebSessionClient persistentWebSessionClient,
      final HttpServletRequest request) {
    final var webSessionAttributeConverter =
        new SpringBasedWebSessionAttributeConverter(conversionService);
    final var webSessionMapper = new WebSessionMapper(webSessionAttributeConverter);
    return new WebSessionRepository(persistentWebSessionClient, webSessionMapper, request);
  }

  @Bean("persistentWebSessionDeletionTaskExecutor")
  public ScheduledThreadPoolExecutor persistentWebSessionDeletionTaskExecutor(
      final WebSessionRepository webSessionRepository) {
    final var executor = createTaskExecutor();
    executor.schedule(
        new SelfSchedulingTask(
            executor,
            new WebSessionDeletionTask(webSessionRepository),
            WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_DELAY),
        WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_DELAY,
        TimeUnit.MILLISECONDS);
    return executor;
  }

  public ScheduledThreadPoolExecutor createTaskExecutor() {
    final var threadFactory =
        Thread.ofPlatform()
            .name("camunda-web-session-deletion-", 0)
            .uncaughtExceptionHandler(
                FatalErrorHandler.uncaughtExceptionHandler(WebSessionRepository.LOGGER))
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
