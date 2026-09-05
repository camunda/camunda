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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

final class CamundaClientImplTest {

  @Test
  void shouldWarnIfRestAddressUsesPlaintextHttp() {
    // given
    final URI restAddress =
        URI.create("http://user:secret@example.com:8080/v1?access_token=secret#section");

    try (final ClientLogCapture logs = ClientLogCapture.start()) {
      // when
      CamundaClientImpl.warnIfInsecureRestAddress(restAddress);

      // then
      final List<String> warningMessages = logs.warningMessages();
      assertThat(warningMessages).hasSize(1);
      assertThat(warningMessages.get(0))
          .contains("http://example.com:8080/v1")
          .doesNotContain("user", "secret", "access_token", "section");
    }
  }

  @Test
  void shouldNotWarnIfRestAddressUsesHttps() {
    // given
    final URI restAddress =
        URI.create("https://user:secret@example.com:443/v1?access_token=secret#section");

    try (final ClientLogCapture logs = ClientLogCapture.start()) {
      // when
      CamundaClientImpl.warnIfInsecureRestAddress(restAddress);

      // then
      assertThat(logs.warningMessages()).isEmpty();
    }
  }

  @Test
  void shouldNotWarnIfRestAddressIsNull() {
    try (final ClientLogCapture logs = ClientLogCapture.start()) {
      // when
      CamundaClientImpl.warnIfInsecureRestAddress(null);

      // then
      assertThat(logs.warningMessages()).isEmpty();
    }
  }

  private static final class ClientLogCapture implements AutoCloseable {

    private final Logger logger;
    private final RecordingAppender appender;

    private ClientLogCapture() {
      logger = (Logger) LogManager.getLogger("io.camunda.client");
      appender = new RecordingAppender();
      appender.start();
      logger.addAppender(appender);
    }

    private static ClientLogCapture start() {
      return new ClientLogCapture();
    }

    private List<String> warningMessages() {
      final List<String> messages = new ArrayList<>();
      for (final LogEvent event : appender.events()) {
        if (Level.WARN.equals(event.getLevel())) {
          messages.add(event.getMessage().getFormattedMessage());
        }
      }
      return messages;
    }

    @Override
    public void close() {
      logger.removeAppender(appender);
      appender.stop();
    }
  }

  private static final class RecordingAppender extends AbstractAppender {

    private final List<LogEvent> events = new ArrayList<>();

    private RecordingAppender() {
      super("CamundaClientImplTestAppender", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(final LogEvent event) {
      events.add(event.toImmutable());
    }

    private List<LogEvent> events() {
      return events;
    }
  }
}
