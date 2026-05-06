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
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.PersistentWebSessionRdbmsClient;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PersistentWebSessionSearchImpl;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;

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
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PersistentWebSessionIndexDescriptor persistentWebSessionIndex() {
    final var indexPrefix = connectConfiguration.getIndexPrefix();
    final var isElasticsearch =
        ConnectionTypes.from(connectConfiguration.getType()).equals(ConnectionTypes.ELASTICSEARCH);
    return new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch);
  }

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PersistentWebSessionClient persistentWebSessionClientSearch(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentWebSessionIndexDescriptor descriptor) {
    return new PersistentWebSessionSearchImpl(searchClient, writeClient, descriptor);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public PersistentWebSessionClient persistentWebSessionClientRdbms(
      final PersistentWebSessionDbReader persistentWebSessionDbReader,
      final PersistentWebSessionWriter persistentWebSessionWriter) {
    return new PersistentWebSessionRdbmsClient(
        persistentWebSessionDbReader, persistentWebSessionWriter);
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

  /**
   * Pin {@link SessionRepositoryFilter} to webapp paths only.
   *
   * <p>{@link EnableSpringHttpSession} auto-registers the filter against {@code /*}, so it runs on
   * every servlet request — including stateless Bearer-token API calls under {@code /v1/**}, {@code
   * /v2/**}, {@code /api/**}, {@code /mcp/**}. The filter wraps the request and triggers a session
   * lookup via {@link PersistentWebSessionClient}, which on the API hot path means a per-request
   * I/O hit against secondary storage that the request does not need.
   *
   * <p>Sessions are only meaningful on webapp endpoints (login, the bundled UIs, OAuth2
   * redirection). Restricting the filter to those URL patterns removes the per-request session
   * lookup from the API request path while keeping webapp session behavior unchanged. Spring Boot
   * suppresses the default global registration when a {@link FilterRegistrationBean} wrapping the
   * same filter bean is present.
   *
   * <p>The patterns are intentionally a strict subset of {@code WebSecurityConfig.WEBAPP_PATHS}
   * (translated from Spring's {@code /**} matchers to servlet {@code /*} prefix mappings). Any
   * webapp path not listed here will lose session support; if a new webapp path is added, this list
   * must be updated.
   */
  @Bean
  public FilterRegistrationBean<SessionRepositoryFilter<?>> sessionRepositoryFilterRegistration(
      @Qualifier("springSessionRepositoryFilter") final SessionRepositoryFilter<?> filter) {
    final FilterRegistrationBean<SessionRepositoryFilter<?>> registration =
        new FilterRegistrationBean<>(filter);
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
    registration.setUrlPatterns(
        List.of(
            "/operate/*",
            "/tasklist/*",
            "/admin/*",
            "/webapp/*",
            "/login/*",
            "/logout",
            "/sso-callback/*",
            "/oauth2/*",
            "/"));
    return registration;
  }

  @Bean("persistentWebSessionDeletionTaskExecutor")
  public ScheduledThreadPoolExecutor persistentWebSessionDeletionTaskExecutor(
      final WebSessionRepository webSessionRepository) {
    final var executor = createTaskExecutor();
    executor.schedule(
        new SelfSchedulingTask(
            executor,
            new WebSessionDeletionTask(webSessionRepository),
            WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_RUN_DELAY),
        WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_INITIAL_DELAY,
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
