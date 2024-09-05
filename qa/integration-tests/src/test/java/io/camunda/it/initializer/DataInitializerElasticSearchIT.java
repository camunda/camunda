/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.sources.DefaultObjectMapperConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.logging.log4j.util.Base64Util;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@SpringBootTest(
    classes = {
      CommonsModuleConfiguration.class,
      BrokerModuleConfiguration.class,
      DefaultObjectMapperConfiguration.class
    },
    properties = {
      "spring.profiles.active=broker,auth-basic",
      "camunda.rest.query.enabled=true",
      "zeebe.broker.exporters.elasticsearch.class-name=io.camunda.zeebe.exporter.ElasticsearchExporter"
    })
@WebAppConfiguration
@AutoConfigureMockMvc
public class DataInitializerElasticSearchIT {

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.9.2")
          .withEnv(
              Map.of(
                  "xpack.security.enabled", "false",
                  "ELASTIC_PASSWORD", ""))
          .withExposedPorts(9200);

  private static final String USERNAME_PREFIX = "default_username_" + UUID.randomUUID();
  private static final String PASSWORD_PREFIX = "default_password";
  private static final int DEFAULT_USERS_COUNT = 2;

  @Autowired private UserServices<UserRecord> userServices;
  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void registerDynamicProperties(final DynamicPropertyRegistry registry) {
    elasticsearch.start();
    registry.add("camunda.database.url", () -> "http://" + elasticsearch.getHttpHostAddress());
    registry.add(
        "zeebe.broker.exporters.elasticsearch.args.url",
        () -> "http://" + elasticsearch.getHttpHostAddress());
    IntStream.range(0, DEFAULT_USERS_COUNT)
        .forEach(
            i -> {
              registry.add("camunda.init.users[" + i + "].username", () -> USERNAME_PREFIX + i);
              registry.add("camunda.init.users[" + i + "].password", () -> PASSWORD_PREFIX + i);
            });
  }

  @BeforeEach
  void setup() throws InterruptedException {
    waitForDataToBeInitialized();
  }

  @AfterAll
  static void shutdown() {
    elasticsearch.stop();
  }

  @Test
  void defaultUsersAreEventuallyCreatedAndCanBeAuthenticated() throws Exception {

    IntStream.range(0, DEFAULT_USERS_COUNT)
        .forEach(
            i -> {
              userServices
                  .findByUsername(USERNAME_PREFIX + i)
                  .ifPresentOrElse(
                      userEntity -> {
                        assertThat(userEntity.value()).isNotNull();
                        assertThat(userEntity.value().email()).isEqualTo(USERNAME_PREFIX + i);
                        assertThat(userEntity.value().name()).isEqualTo(USERNAME_PREFIX + i);
                      },
                      () -> fail(USERNAME_PREFIX + i + " does not exist"));
            });

    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post("/v2/users/search")
            .accept("application/json")
            .contentType(MediaType.APPLICATION_JSON)
            .header(
                "Authorization",
                "Basic " + Base64Util.encode(USERNAME_PREFIX + "0:" + PASSWORD_PREFIX + "0"));
    mockMvc.perform(request).andExpect(status().isOk()).andReturn();
  }

  private void waitForDataToBeInitialized() throws InterruptedException {
    var retry = 0;
    while (retry++ < 500) {
      try {
        if (userServices
            .withAuthentication(RequestMapper.getAuthenticationNoTenant())
            .findByUsername(USERNAME_PREFIX + 0)
            .isPresent()) {
          return;
        }
      } catch (final Exception ignored) {

      }
      Thread.sleep(100);
    }
  }
}
