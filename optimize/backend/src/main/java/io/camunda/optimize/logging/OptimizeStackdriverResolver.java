/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolver;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverConfig;
import org.apache.logging.log4j.layout.template.json.util.JsonWriter;

/** Custom resolver that emits Stackdriver-specific fields used by the JSON template layout. */
public final class OptimizeStackdriverResolver implements EventResolver {

  private static final String ERROR_TYPE =
      "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";

  private static final EventResolver TYPE_RESOLVER =
      new EventResolver() {
        @Override
        public boolean isResolvable(final LogEvent value) {
          return value.getLevel().isMoreSpecificThan(Level.ERROR);
        }

        @Override
        public void resolve(final LogEvent value, final JsonWriter jsonWriter) {
          jsonWriter.writeString(ERROR_TYPE);
        }
      };

  private static final EventResolver REPORT_LOCATION_RESOLVER =
      new EventResolver() {
        @Override
        public boolean isResolvable(final LogEvent value) {
          return value.getLevel().isMoreSpecificThan(Level.ERROR)
              && value.getSource() != null
              && value.getThrownProxy() == null;
        }

        @Override
        public void resolve(final LogEvent value, final JsonWriter jsonWriter) {
          final var stackTrace = value.getSource();
          jsonWriter.writeObjectStart();
          jsonWriter.writeObjectKey("filePath");
          jsonWriter.writeString(stackTrace.getFileName());
          jsonWriter.writeSeparator();
          jsonWriter.writeObjectKey("functionName");
          jsonWriter.writeString(stackTrace.getMethodName());
          jsonWriter.writeSeparator();
          jsonWriter.writeObjectKey("lineNumber");
          jsonWriter.writeNumber(stackTrace.getLineNumber());
          jsonWriter.writeObjectEnd();
        }
      };

  private final EventResolver internalResolver;

  public OptimizeStackdriverResolver(final TemplateResolverConfig config) {
    internalResolver = createInternalResolver(config);
  }

  private static EventResolver createInternalResolver(final TemplateResolverConfig config) {
    final String fieldName = config.getString("field");
    return switch (fieldName) {
      case "type" -> TYPE_RESOLVER;
      case "reportLocation" -> REPORT_LOCATION_RESOLVER;
      default -> throw new IllegalArgumentException("unknown field: " + config);
    };
  }

  @Override
  public boolean isResolvable(final LogEvent value) {
    return internalResolver.isResolvable(value);
  }

  @Override
  public void resolve(final LogEvent logEvent, final JsonWriter jsonWriter) {
    internalResolver.resolve(logEvent, jsonWriter);
  }
}
