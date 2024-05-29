/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.apache.logging.log4j.core.test.junit.Named;
import org.junit.jupiter.api.Test;

/**
 * This test ensures that StackdriverLayout defined in zeebe-util library is working as expected. If
 * the test is failing, then probably smth was changed on Zeebe side and we need to make adjustments
 * accordingly, including dist/config/log4j2.xml file and docs on logging.
 */
@LoggerContextSource("log4j2-test.xml")
class StackdriverJSONLayoutTest {

  private static final String STACKDRIVER_APPENDER_NAME = "Stackdriver";
  private static final ObjectReader JSON_READER = new ObjectMapper().reader();

  @Test
  void testLayout(final LoggerContext ctx, @Named(STACKDRIVER_APPENDER_NAME) final ListAppender app)
      throws Exception {
    // given
    // having Stackdriver appender activated
    ctx.getRootLogger().addAppender(app);
    final Logger logger = ctx.getLogger(StackdriverJSONLayoutTest.class.getName());

    // when
    logger.warn("Test message");

    // then
    final List<String> messages = app.getMessages();
    assertThat(messages).hasSize(1);

    final Map<String, Map<String, String>> logMap =
        JSON_READER.withValueToUpdate(new HashMap<String, String>()).readValue(messages.get(0));
    assertThat(logMap)
        .containsEntry(
            "serviceContext",
            Map.of(
                "service", "customService",
                "version", "customVersion"));
  }
}
