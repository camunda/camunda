/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.spi.SessionPersistencePort;
import io.camunda.auth.spring.session.WebSessionDeletionTask;
import io.camunda.auth.spring.session.WebSessionMapper;
import io.camunda.auth.spring.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.auth.spring.session.WebSessionRepository;
import io.camunda.auth.starter.condition.ConditionalOnPersistentWebSessionEnabled;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@AutoConfiguration
@EnableSpringHttpSession
@ConditionalOnPersistentWebSessionEnabled
@ConditionalOnBean(SessionPersistencePort.class)
public class CamundaWebSessionAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WebSessionRepository webSessionRepository(
      final SessionPersistencePort sessionPersistencePort,
      final GenericConversionService conversionService,
      final HttpServletRequest request) {
    final var webSessionAttributeConverter =
        new SpringBasedWebSessionAttributeConverter(conversionService);
    final var webSessionMapper = new WebSessionMapper(webSessionAttributeConverter);
    return new WebSessionRepository(sessionPersistencePort, webSessionMapper, request);
  }

  @Bean("persistentWebSessionDeletionTaskExecutor")
  @ConditionalOnMissingBean(name = "persistentWebSessionDeletionTaskExecutor")
  public ScheduledThreadPoolExecutor persistentWebSessionDeletionTaskExecutor(
      final WebSessionRepository webSessionRepository) {
    final var threadFactory =
        Thread.ofPlatform().name("camunda-web-session-deletion-", 0).factory();
    final var executor = new ScheduledThreadPoolExecutor(0, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    executor.allowCoreThreadTimeOut(true);
    executor.setKeepAliveTime(1, TimeUnit.MINUTES);
    executor.setCorePoolSize(1);
    executor.schedule(
        new SelfSchedulingTask(
            executor,
            new WebSessionDeletionTask(webSessionRepository),
            WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_RUN_DELAY),
        WebSessionDeletionTask.DELETE_EXPIRED_SESSIONS_INITIAL_DELAY,
        TimeUnit.MILLISECONDS);
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
