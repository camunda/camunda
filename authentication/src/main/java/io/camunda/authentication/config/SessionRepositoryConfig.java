/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.handler.session.CamundaSessionRepository;
import io.camunda.authentication.handler.session.ConditionalOnSessionPersistence;
import io.camunda.authentication.handler.session.NoOpSessionDocumentStorageClient;
import io.camunda.authentication.handler.session.SessionDocumentMapper;
import io.camunda.authentication.handler.session.WebSession;
import io.camunda.search.security.SessionDocumentStorageClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@Configuration
@ConditionalOnSessionPersistence
@EnableSpringHttpSession
public class SessionRepositoryConfig {

  @Bean("sessionThreadPoolScheduler")
  public ThreadPoolTaskScheduler getTaskScheduler() {
    final ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(5);
    executor.setThreadNamePrefix("camunda_session_");
    executor.initialize();
    return executor;
  }

  @Bean
  @ConditionalOnMissingBean(SessionDocumentStorageClient.class)
  public SessionDocumentStorageClient sessionDocumentStorageClient() {
    return new NoOpSessionDocumentStorageClient();
  }

  @Bean
  public SessionRepository<WebSession> camundaSessionRepository(
      @Qualifier("sessionThreadPoolScheduler") final ThreadPoolTaskScheduler taskScheduler,
      final SessionDocumentMapper sessionDocumentMapper,
      final SessionDocumentStorageClient sessionStorageClient,
      final HttpServletRequest request) {
    return new CamundaSessionRepository(
        taskScheduler, sessionDocumentMapper, sessionStorageClient, request);
  }
}
