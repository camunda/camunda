/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Before;
import org.junit.Test;

public class StackdriverJSONLayoutTest {

  private static final Logger LOGGER = LogManager.getLogger();
  private LoggerContext context = (LoggerContext) LogManager.getContext(false);
  private Writer logOutput;
  private ObjectReader jsonReader = new ObjectMapper().reader();

  @Before
  public void before() {
    logOutput = new StringWriter();
  }

  @Test
  public void testJSONOutput() throws IOException {
    // Given
    createTestAppenderWithLayout(new StackdriverJSONLayout());
    // when
    LOGGER.error("Should appear as JSON formatted ouput");
    // then
    final Map<String, String> jsonMap = logOutputToJSONMap();
    assertThat(jsonMap)
        .containsKeys(
            "logger", "message", "severity", "thread", "timestampNanos", "timestampSeconds");
    assertThat(jsonMap).containsEntry("message", "Should appear as JSON formatted ouput");
    assertThat(jsonMap).containsEntry("logger", LOGGER.getName());
  }

  private Map<String, String> logOutputToJSONMap() throws IOException {
    return jsonReader
        .withValueToUpdate(new HashMap<String, String>())
        .readValue(logOutput.toString());
  }

  private void createTestAppenderWithLayout(StringLayout layout) {
    final Appender appender =
        WriterAppender.createAppender(layout, null, logOutput, "test", false, false);
    final LoggerConfig loggerConfig = context.getConfiguration().getRootLogger();
    loggerConfig.addAppender(appender, Level.ALL, null);
    context.updateLoggers();
    appender.start();
  }
}
