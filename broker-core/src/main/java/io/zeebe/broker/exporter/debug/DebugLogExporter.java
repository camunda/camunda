/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;
import java.util.Collections;
import org.slf4j.Logger;

public class DebugLogExporter implements Exporter {

  private Logger log;
  private LogLevel logLevel;
  private DebugExporterConfiguration configuration;
  private ObjectMapper objectMapper;

  @Override
  public void configure(Context context) {
    log = context.getLogger();
    configuration = context.getConfiguration().instantiate(DebugExporterConfiguration.class);
    logLevel = configuration.getLogLevel();
  }

  @Override
  public void open(Controller controller) {
    log("Debug exporter opened");
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    if (configuration.prettyPrint) {
      objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
  }

  @Override
  public void close() {
    log("Debug exporter closed");
  }

  @Override
  public void export(Record record) {
    try {
      log("{}", objectMapper.writeValueAsString(record));
    } catch (JsonProcessingException e) {
      log("Failed to serialize object '{}' to JSON", record, e);
    }
  }

  public static ExporterCfg defaultConfig(final boolean prettyPrint) {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setId("debug");
    exporterCfg.setClassName(DebugLogExporter.class.getName());
    exporterCfg.setArgs(Collections.singletonMap("prettyPrint", prettyPrint));
    return exporterCfg;
  }

  public void log(String message, Object... args) {
    switch (logLevel) {
      case TRACE:
        log.trace(message, args);
        break;
      case DEBUG:
        log.debug(message, args);
        break;
      case INFO:
        log.info(message, args);
        break;
      case WARN:
        log.warn(message, args);
        break;
      case ERROR:
        log.error(message, args);
        break;
    }
  }

  public static class DebugExporterConfiguration {
    public String logLevel = "debug";
    public boolean prettyPrint = false;

    LogLevel getLogLevel() {
      return LogLevel.valueOf(logLevel.trim().toUpperCase());
    }
  }

  enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
  }
}
