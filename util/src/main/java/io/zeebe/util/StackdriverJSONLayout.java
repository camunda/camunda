/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.util.StringBuilderWriter;

/**
 * Idea and code (slightly changed) from
 * <li><a href=
 *     "https://k11i.biz/blog/2018/10/03/stackdriver-logging-friendly-layout-java/">https://k11i.biz/blog/2018/10/03/stackdriver-logging-friendly-layout-java/</a>
 */
@Plugin(name = "StackdriverJSONLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE)
public class StackdriverJSONLayout extends AbstractStringLayout {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public StackdriverJSONLayout() {
    this(StandardCharsets.UTF_8);
  }

  protected StackdriverJSONLayout(Charset charset) {
    super(charset);
  }

  @PluginFactory
  public static StackdriverJSONLayout create() {
    return new StackdriverJSONLayout();
  }

  @Override
  public String toSerializable(LogEvent event) {
    try (StringBuilderWriter writer = new StringBuilderWriter()) {
      try {
        OBJECT_MAPPER.writeValue(writer, logEventToMap(event));
        writer.append('\n');
        return writer.toString();
      } catch (IOException e) {
        return null;
      }
    }
  }

  protected Map<String, Object> logEventToMap(LogEvent event) {
    final Map<String, Object> map = new LinkedHashMap<>();

    map.put("timestampSeconds", event.getTimeMillis() / 1000);
    map.put("timestampNanos", (event.getTimeMillis() % 1000) * 1_000_000);
    // 'Level' is equal to 'severity' in gcloud/stackdriver
    putIfNotNull("severity", event.getLevel().toString(), map);
    putIfNotNull("thread", event.getThreadName(), map);
    putIfNotNull("logger", event.getLoggerName(), map);
    putIfNotNull("message", event.getMessage().getFormattedMessage(), map);
    putIfNotNull("exception", getThrowableAsString(event.getThrownProxy()), map);
    map.put("context", event.getContextData().toMap());

    return map;
  }

  private String getThrowableAsString(ThrowableProxy thrownProxy) {
    if (thrownProxy != null) {
      return thrownProxy.getExtendedStackTraceAsString();
    }
    return null;
  }

  private void putIfNotNull(String key, String value, Map<String, Object> map) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
