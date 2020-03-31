/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StackdriverJSONLayoutTest {

  private static final ObjectReader OBJECT_READER = new ObjectMapper().reader();

  private Logger logger;
  private Writer logTarget;
  private WriterAppender appender;

  @Before
  public void before() {
    logger = (Logger) LogManager.getLogger();
    logTarget = new StringWriter();
    appender = createAndStartAppender(new StackdriverJSONLayout(), logTarget);
    logger.addAppender(appender);
  }

  @After
  public void tearDown() {
    logger.removeAppender(appender);
  }

  @Test
  public void testJSONOutput() throws IOException {
    // when
    logger.error("Should appear as JSON formatted output");

    // then
    final Map<String, String> jsonMap = writerToMap(logTarget);

    SoftAssertions.assertSoftly(
        softly ->
            softly
                .assertThat(jsonMap)
                .containsKeys(
                    "logger", "message", "severity", "thread", "timestampNanos", "timestampSeconds")
                .containsEntry("message", "Should appear as JSON formatted output")
                .containsEntry("severity", "ERROR")
                .containsEntry("logger", logger.getName()));
  }

  private Map<String, String> writerToMap(Writer logTarget) throws JsonProcessingException {
    return OBJECT_READER
        .withValueToUpdate(new HashMap<String, String>())
        .readValue(logTarget.toString());
  }

  private WriterAppender createAndStartAppender(StringLayout layout, Writer logTarget) {
    final WriterAppender appender =
        WriterAppender.createAppender(layout, null, logTarget, "test", false, false);
    appender.start();
    return appender;
  }
}
