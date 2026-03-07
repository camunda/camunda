/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import io.camunda.auth.persist.rdbms.RdbmsSessionPersistenceAdapter;
import io.camunda.auth.persist.rdbms.WebSessionMapper;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import io.camunda.auth.spring.session.WebSessionRepository;
import java.util.List;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full Spring Boot application context integration test simulating a "Hub"-like consumer using the
 * auth library with RDBMS (PostgreSQL) persistence and basic authentication.
 *
 * <p>Boots a real Spring Boot app context with the camunda-auth-spring-boot-starter auto-config,
 * verifying that beans are wired correctly and security filter chains work end-to-end.
 */
@SpringBootTest(
    classes = {RdbmsConsumerIT.TestApp.class, RdbmsConsumerIT.RdbmsConsumerTestConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "camunda.auth.method=basic",
      "camunda.auth.basic.secondary-storage-available=true",
      "camunda.auth.security.webapp-enabled=true",
      "camunda.auth.security.csrf-enabled=false",
      "camunda.auth.persistence.type=rdbms",
      "camunda.auth.persistence.mode=standalone",
      "camunda.persistent.sessions.enabled=true",
      "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
      "spring.application.name=rdbms-consumer-it"
    })
@AutoConfigureMockMvc
@Testcontainers
class RdbmsConsumerIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureDataSource(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private ApplicationContext applicationContext;
  @Autowired private MockMvc mockMvc;

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
    assertThat(port).isNotNull().isInstanceOf(RdbmsSessionPersistenceAdapter.class);
  }

  @Test
  void shouldWireWebSessionRepository() {
    assertThat(applicationContext.getBean(WebSessionRepository.class)).isNotNull();
  }

  @Test
  void shouldWireMultipleSecurityFilterChains() {
    final var chains = applicationContext.getBeansOfType(SecurityFilterChain.class);
    assertThat(chains).isNotEmpty();
    assertThat(chains.size()).isGreaterThanOrEqualTo(2);
  }

  // -- Basic auth login tests --

  @Test
  void shouldRejectUnauthenticatedApiRequest() throws Exception {
    mockMvc.perform(get("/v1/test")).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldAllowAuthenticatedApiRequest() throws Exception {
    mockMvc
        .perform(get("/v1/test").with(httpBasic("testuser", "password")))
        .andExpect(status().isOk());
  }

  // -- Session persistence tests --

  @Test
  void shouldPersistSessionInPostgres() throws Exception {
    final var sessionPort = applicationContext.getBean(SessionPersistencePort.class);

    // Perform login via the webapp form login path
    mockMvc
        .perform(
            post("/login")
                .param("username", "testuser")
                .param("password", "password")
                .with(csrf()))
        .andExpect(status().is3xxRedirection());

    // After login, at least one session should exist in the RDBMS store
    final var sessions = sessionPort.findAll();
    assertThat(sessions).isNotEmpty();
  }

  // -- Unprotected paths --

  @Test
  void shouldAllowUnprotectedActuatorPath() throws Exception {
    // /actuator/** is in the default unprotected paths
    // It may return 404 (no actuator configured) but should not return 401
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(
            result -> {
              final int statusCode = result.getResponse().getStatus();
              assertThat(statusCode).isNotEqualTo(401);
            });
  }

  // -- Security headers (webapp filter chain) --

  @Test
  void shouldIncludeSecurityHeadersOnWebappResponse() throws Exception {
    mockMvc
        .perform(
            get("/login")
                .with(httpBasic("testuser", "password")))
        .andExpect(header().exists("X-Content-Type-Options"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  // -- Test application and configuration --

  @SpringBootApplication(
      exclude = {
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
  static class RdbmsConsumerTestConfig {

    @Bean
    BasicAuthMembershipResolver basicAuthMembershipResolver() {
      return username ->
          CamundaAuthentication.of(
              b -> b.user(username).groupIds(List.of("test-group")).roleIds(List.of("test-role")));
    }

    @Bean
    UserDetailsService userDetailsService() {
      return new InMemoryUserDetailsManager(
          User.withUsername("testuser")
              .password("{noop}password")
              .roles("USER")
              .build());
    }

    @Bean
    SessionPersistencePort sessionPersistencePort(final WebSessionMapper webSessionMapper) {
      return new RdbmsSessionPersistenceAdapter(webSessionMapper);
    }

    /**
     * Runs Liquibase migrations to create the AUTH_WEB_SESSION table (and other auth tables)
     * required by the RDBMS persistence adapter.
     */
    @Bean
    org.springframework.boot.CommandLineRunner liquibaseMigrationRunner(
        final DataSource dataSource) {
      return args -> {
        try (var connection = dataSource.getConnection()) {
          final var database =
              DatabaseFactory.getInstance()
                  .findCorrectDatabaseImplementation(new JdbcConnection(connection));
          try (var liquibase =
              new Liquibase(
                  "db/changelog/auth/auth-changelog-master.xml",
                  new ClassLoaderResourceAccessor(),
                  database)) {
            liquibase.update("");
          }
        }
      };
    }
  }
}
