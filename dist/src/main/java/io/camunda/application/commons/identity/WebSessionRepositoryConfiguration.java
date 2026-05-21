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
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@Configuration
@ConditionalOnPersistentWebSessionEnabled
@ConditionalOnRestGatewayEnabled
public class WebSessionRepositoryConfiguration {

  private final ConnectConfiguration connectConfiguration;

  public WebSessionRepositoryConfiguration(final ConnectConfiguration connectConfiguration) {
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

  /**
   * Non-PT configuration: single cluster-wide {@link WebSessionRepository} plus the {@link
   * EnableSpringHttpSession} integration that registers a servlet-container-wide {@code
   * SessionRepositoryFilter}.
   *
   * <p>Gated on the absence of any {@code camunda.physical-tenants.*} entry. When PTs are
   * configured, the {@link PerPhysicalTenant} nested config below produces per-tenant {@link
   * WebSessionRepository}s instead and the PT chains install their own per-chain {@code
   * SessionRepositoryFilter}s — so the global {@link EnableSpringHttpSession} integration must NOT
   * register a servlet-container-wide one.
   *
   * <p>{@code camunda.physical-tenants} is a structured map property (keys are tenant ids), so
   * checking for its presence requires binding the prefix — a flat
   * {@code @ConditionalOnExpression("${camunda.physical-tenants:}")} would always resolve to the
   * empty default. {@link NoPhysicalTenantsConfiguredCondition} performs the bind and returns true
   * iff the result is empty.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableSpringHttpSession
  @Conditional(NoPhysicalTenantsConfiguredCondition.class)
  static class SingleTenant {

    private final GenericConversionService conversionService;

    SingleTenant(final GenericConversionService conversionService) {
      this.conversionService = conversionService;
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
              WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_RUN_DELAY),
          WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_INITIAL_DELAY,
          TimeUnit.MILLISECONDS);
      return executor;
    }

    private static ScheduledThreadPoolExecutor createTaskExecutor() {
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
  }

  /**
   * Per-physical-tenant configuration: produces one {@link WebSessionRepository} per tenant,
   * exposed as a {@code Map<String, WebSessionRepository>} keyed by tenant id. Injected directly
   * into {@code PhysicalTenantSecurityConfiguration}, which looks up the right instance per chain.
   *
   * <p>Notably absent: {@link EnableSpringHttpSession}. The PT setup wires per-chain {@code
   * SessionRepositoryFilter}s manually rather than relying on a servlet-container-wide one.
   *
   * <p>Storage isolation is structural: each tenant's repository owns its own backing client — no
   * shared backend, no key-prefixing decorator. The backing client is an in-memory {@link
   * InMemoryPersistentWebSessionClient}; per-tenant durable storage is out of scope for the PoC.
   * Sessions live for the lifetime of the process. Because sessions are in-memory and die with the
   * process, no per-tenant {@code WebSessionDeletionTask} runs.
   */
  @Configuration(proxyBeanMethods = false)
  @Conditional(PhysicalTenantsConfiguredCondition.class)
  static class PerPhysicalTenant {

    @Bean
    public Map<String, WebSessionRepository> ptWebSessionRepositories(
        final PhysicalTenantResolver physicalTenantResolver,
        final GenericConversionService conversionService,
        final HttpServletRequest request) {
      final var mapper =
          new WebSessionMapper(new SpringBasedWebSessionAttributeConverter(conversionService));
      final var repositories = new LinkedHashMap<String, WebSessionRepository>();
      physicalTenantResolver
          .getAll()
          .keySet()
          .forEach(
              tenantId ->
                  repositories.put(
                      tenantId,
                      new WebSessionRepository(
                          new InMemoryPersistentWebSessionClient(), mapper, request)));
      return Map.copyOf(repositories);
    }
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
