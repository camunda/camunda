/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

public class StackdriverJSONLayoutTest {

  private Logger logger = LogManager.getLogger();
  private LoggerContext context = (LoggerContext) LogManager.getContext(false);
  private Writer logOutput;
  private ObjectReader jsonReader = new ObjectMapper().reader();

  @Before
  public void before() {
    logOutput = new StringWriter();
  }

  @Test
  public void testJSONOutput() throws JsonMappingException, JsonProcessingException {
    // Given
    createTestAppenderWithLayout(new StackdriverJSONLayout());
    // when
    logger.error("Should appear as JSON formatted ouput");
    // then
    Map<String, String> jsonMap = logOutputToJSONMap();
    assertThat(jsonMap).containsKeys("logger", "message", "severity", "thread", "timestampNanos", "timestampSeconds");
    assertThat(jsonMap).containsEntry("message", "Should appear as JSON formatted ouput");
    assertThat(jsonMap).containsEntry("logger", logger.getName());
  }

  private Map<String, String> logOutputToJSONMap() throws JsonProcessingException, JsonMappingException {
    return jsonReader.withValueToUpdate(new HashMap<String, String>()).readValue(logOutput.toString());
  }

  private void createTestAppenderWithLayout(StringLayout layout) {
    Appender appender = WriterAppender.createAppender(layout, null, logOutput, "test", false, false);
    LoggerConfig loggerConfig = context.getConfiguration().getRootLogger();
    loggerConfig.addAppender(appender, Level.ALL, null);
    context.updateLoggers();
    appender.start();
  }

}
