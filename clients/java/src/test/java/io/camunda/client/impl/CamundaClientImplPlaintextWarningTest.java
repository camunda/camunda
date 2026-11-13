/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client warns once at creation when the REST API is reached over plain HTTP, so users notice
 * that requests and credentials leave the client unencrypted (#36658).
 */
final class CamundaClientImplPlaintextWarningTest {

  private static final String LOGGER_NAME = "io.camunda.client";
  private static final URI GRPC_ADDRESS = URI.create("http://localhost:26500");

  private ListAppender logCapture;

  @BeforeEach
  void setUp() {
    logCapture = new ListAppender("capture-plaintext-warning");
    logCapture.start();
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    final LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, Level.ALL, true);
    loggerConfig.addAppender(logCapture, null, null);
    ctx.getConfiguration().addLogger(LOGGER_NAME, loggerConfig);
    ctx.updateLoggers();
  }

  @AfterEach
  void tearDown() {
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().removeLogger(LOGGER_NAME);
    ctx.updateLoggers();
    logCapture.stop();
  }

  @Test
  void shouldWarnOnceWhenRestAddressUsesHttp() {
    // when
    try (final CamundaClient ignored =
        CamundaClient.newClientBuilder()
            .grpcAddress(GRPC_ADDRESS)
            .restAddress(URI.create("http://user:secret@camunda.example.com:8080"))
            .build()) {
      // then
      final List<String> warnings = plaintextWarnings();
      assertThat(warnings).hasSize(1);
      assertThat(warnings.get(0))
          .contains("http://camunda.example.com:8080")
          .doesNotContain("secret");
    }
  }

  @Test
  void shouldNotWarnWhenRestAddressUsesHttps() {
    // when
    try (final CamundaClient ignored =
        CamundaClient.newClientBuilder()
            .grpcAddress(GRPC_ADDRESS)
            .restAddress(URI.create("https://camunda.example.com:8443"))
            .build()) {
      // then
      assertThat(plaintextWarnings()).isEmpty();
    }
  }

  private List<String> plaintextWarnings() {
    return logCapture.getEvents().stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(LogEvent::getMessage)
        .map(Message::getFormattedMessage)
        .filter(message -> message.contains("plaintext HTTP"))
        .collect(Collectors.toList());
  }
}
