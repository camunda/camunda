/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.SessionData;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import io.camunda.auth.persist.elasticsearch.ElasticsearchSessionPersistenceAdapter;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full Spring Boot application context integration test simulating an "OC"-like consumer using the
 * auth library with Elasticsearch persistence and basic authentication.
 *
 * <p>Boots a real Spring Boot app context with the camunda-auth-spring-boot-starter auto-config and
 * an Elasticsearch testcontainer, verifying that beans are wired correctly and session persistence
 * works end-to-end.
 */
@SpringBootTest(
    classes = {
      ElasticsearchConsumerIT.TestApp.class,
      ElasticsearchConsumerIT.ElasticsearchConsumerTestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "camunda.auth.method=basic",
      "camunda.auth.basic.secondary-storage-available=true",
      "camunda.auth.persistence.type=elasticsearch",
      "camunda.auth.persistence.mode=standalone",
      "camunda.persistent.sessions.enabled=true",
      "spring.application.name=es-consumer-it"
    })
@Testcontainers
class ElasticsearchConsumerIT {

  private static final String SESSION_INDEX = "camunda-auth-web-session";

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.17.0")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node");

  @DynamicPropertySource
  static void configureElasticsearch(final DynamicPropertyRegistry registry) {
    registry.add("camunda.auth.persistence.elasticsearch.url", ELASTICSEARCH::getHttpHostAddress);
  }

  @Autowired private ApplicationContext applicationContext;

  // -- Auto-configuration wiring tests --

  @Test
  void shouldWireCamundaAuthenticationProvider() {
    assertThat(applicationContext.getBean(CamundaAuthenticationProvider.class)).isNotNull();
  }

  @Test
  void shouldWireAuthFailureHandler() {
    assertThat(applicationContext.getBean(AuthFailureHandler.class)).isNotNull();
  }

  @Test
  void shouldWireSessionPersistencePort() {
    final var port = applicationContext.getBean(SessionPersistencePort.class);
    assertThat(port).isNotNull().isInstanceOf(ElasticsearchSessionPersistenceAdapter.class);
  }

  @Test
  void shouldWireSecurityFilterChains() {
    final var chains = applicationContext.getBeansOfType(SecurityFilterChain.class);
    assertThat(chains).isNotEmpty();
  }

  @Test
  void shouldWireElasticsearchClient() {
    assertThat(applicationContext.getBean(ElasticsearchClient.class)).isNotNull();
  }

  // -- Session persistence tests --

  @Test
  void shouldPersistAndRetrieveSessionInElasticsearch() {
    final var sessionPort = applicationContext.getBean(SessionPersistencePort.class);

    final var sessionData =
        new SessionData(
            "test-session-001",
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            1800,
            Map.of());

    sessionPort.save(sessionData);
    refreshIndex();

    final var retrieved = sessionPort.findById("test-session-001");
    assertThat(retrieved).isNotNull();
    assertThat(retrieved.id()).isEqualTo("test-session-001");
    assertThat(retrieved.maxInactiveIntervalInSeconds()).isEqualTo(1800);

    // Cleanup
    sessionPort.deleteById("test-session-001");
  }

  @Test
  void shouldDeleteSessionFromElasticsearch() {
    final var sessionPort = applicationContext.getBean(SessionPersistencePort.class);

    final var sessionData =
        new SessionData(
            "test-session-delete",
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            1800,
            Map.of());

    sessionPort.save(sessionData);
    refreshIndex();

    sessionPort.deleteById("test-session-delete");
    refreshIndex();

    final var retrieved = sessionPort.findById("test-session-delete");
    assertThat(retrieved).isNull();
  }

  @Test
  void shouldDeleteExpiredSessionsFromElasticsearch() {
    final var sessionPort = applicationContext.getBean(SessionPersistencePort.class);

    // Create an already-expired session: last accessed 2 hours ago, max inactive 1 second
    final long twoHoursAgo = System.currentTimeMillis() - 7_200_000;
    final var expiredSession =
        new SessionData("test-session-expired", twoHoursAgo, twoHoursAgo, 1, Map.of());

    // Create a valid session
    final var validSession =
        new SessionData(
            "test-session-valid",
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            3600,
            Map.of());

    sessionPort.save(expiredSession);
    sessionPort.save(validSession);
    refreshIndex();

    // Delete expired sessions
    sessionPort.deleteExpired();
    refreshIndex();

    // The expired session should be gone
    assertThat(sessionPort.findById("test-session-expired")).isNull();
    // The valid session should still exist
    assertThat(sessionPort.findById("test-session-valid")).isNotNull();

    // Cleanup
    sessionPort.deleteById("test-session-valid");
  }

  // -- Helper methods --

  private void refreshIndex() {
    final var esClient = applicationContext.getBean(ElasticsearchClient.class);
    try {
      esClient.indices().refresh(r -> r.index(SESSION_INDEX));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to refresh ES index", e);
    }
  }

  // -- Test application and configuration --

  @SpringBootApplication(
      exclude = {
        DataSourceAutoConfiguration.class,
        io.camunda.auth.starter.CamundaWebappSecurityAutoConfiguration.class
      })
  static class TestApp {

    @RestController
    static class TestController {
      @GetMapping("/v1/test")
      String test() {
        return "ok";
      }
    }
  }

  @TestConfiguration
  static class ElasticsearchConsumerTestConfig {

    @Bean
    BasicAuthMembershipResolver basicAuthMembershipResolver() {
      return username ->
          CamundaAuthentication.of(
              b -> b.user(username).groupIds(List.of("test-group")).roleIds(List.of("test-role")));
    }

    @Bean
    ElasticsearchClient elasticsearchClient() {
      final RestClient restClient =
          RestClient.builder(HttpHost.create(ELASTICSEARCH.getHttpHostAddress())).build();
      final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
      return new ElasticsearchClient(transport);
    }

    @Bean
    SessionPersistencePort sessionPersistencePort(
        final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchSessionPersistenceAdapter(elasticsearchClient);
    }
  }
}
