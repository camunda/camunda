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
package io.camunda.process.test.impl.runtime.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.testcontainers.containers.output.OutputFrame;

/**
 * A logger for Docker containers that produce structured JSON log messages. It is inspired by
 * {@link org.testcontainers.containers.output.Slf4jLogConsumer} but forward the log messages with
 * the correct log level. STDOUT is mapped to INFO level and STDERR to ERROR level. The default
 * level is INFO.
 */
public class Slf4jJsonLogConsumer implements Consumer<OutputFrame> {

  private static final Map<String, Level> LOG_LEVEL_BY_SEVERITY;

  static {
    LOG_LEVEL_BY_SEVERITY = new HashMap<>();
    LOG_LEVEL_BY_SEVERITY.put("TRACE", Level.TRACE);
    LOG_LEVEL_BY_SEVERITY.put("DEBUG", Level.DEBUG);
    LOG_LEVEL_BY_SEVERITY.put("INFO", Level.INFO);
    LOG_LEVEL_BY_SEVERITY.put("WARNING", Level.WARN);
    LOG_LEVEL_BY_SEVERITY.put("ERROR", Level.ERROR);
  }

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final Logger logger;
  private final Class<? extends LogEntry> logEntryType;

  public Slf4jJsonLogConsumer(final Logger logger, final Class<? extends LogEntry> logEntryType) {
    this.logger = logger;
    this.logEntryType = logEntryType;
  }

  @Override
  public void accept(final OutputFrame outputFrame) {
    final OutputFrame.OutputType outputType = outputFrame.getType();
    final String utf8String = outputFrame.getUtf8StringWithoutLineEnding();

    switch (outputType) {
      case END:
        break;

      case STDOUT:
        if (isJsonLogMessage(utf8String)) {
          logJsonMessage(utf8String);
        } else {
          logger.info("{}", utf8String);
        }
        break;

      case STDERR:
        logger.error("{}", utf8String);
        break;

      default:
        throw new IllegalArgumentException("Unexpected outputType " + outputType);
    }
  }

  private static boolean isJsonLogMessage(final String logMessage) {
    return logMessage.startsWith("{") && logMessage.endsWith("}");
  }

  private void logJsonMessage(final String jsonMessage) {
    try {
      final LogEntry logEntry = objectMapper.readValue(jsonMessage, logEntryType);

      final Level logLevel = LOG_LEVEL_BY_SEVERITY.getOrDefault(logEntry.getSeverity(), Level.INFO);
      final String loggerName = logEntry.getLoggerName();
      final String message = logEntry.getMessage();

      logger.atLevel(logLevel).log("{} - {}", loggerName, message);

    } catch (final JsonProcessingException e) {
      // fallback, if the log format is different
      logger.info("{}", jsonMessage);
    }
  }
}
