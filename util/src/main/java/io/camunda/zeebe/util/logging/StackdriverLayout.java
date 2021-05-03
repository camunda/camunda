/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.zeebe.util.logging.stackdriver.StackdriverLogEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.impl.LocationAware;
import org.apache.logging.log4j.core.layout.AbstractLayout;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;

/**
 * Stackdriver JSON layout as described here:
 * https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry
 * https://cloud.google.com/error-reporting/docs/formatting-error-messages
 * https://cloud.google.com/logging/docs/agent/configuration#special-fields
 *
 * <p>The layout produces log output which fully integrates with Google's ErrorReporting, as well as
 * properly unwrapping the context map to allow adding ad-hoc fields such as the trace and spanId to
 * integrated with Cloud Trace.
 */
@Plugin(name = "StackdriverLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE)
public final class StackdriverLayout extends AbstractLayout<byte[]> implements LocationAware {

  private static final ObjectWriter WRITER =
      new ObjectMapper().writerFor(StackdriverLogEntry.class);
  private static final String CONTENT_TYPE = "application/json; charset=utf-8";
  private static final String DEFAULT_SERVICE_VERSION = "development";
  private static final String DEFAULT_SERVICE_NAME = "zeebe";
  private static final byte[] EMPTY = new byte[0];
  private static final byte[] LINE_SEPARATOR =
      System.lineSeparator().getBytes(StandardCharsets.UTF_8);

  private final String serviceName;
  private final String serviceVersion;

  public StackdriverLayout() {
    this(new DefaultConfiguration(), DEFAULT_SERVICE_NAME, DEFAULT_SERVICE_VERSION);
  }

  public StackdriverLayout(
      final Configuration configuration, final String serviceName, final String serviceVersion) {
    super(configuration, null, null);

    if (serviceName == null || serviceName.isBlank()) {
      this.serviceName = DEFAULT_SERVICE_NAME;
    } else {
      this.serviceName = serviceName;
    }

    if (serviceVersion == null || serviceVersion.isBlank()) {
      this.serviceVersion = DEFAULT_SERVICE_VERSION;
    } else {
      this.serviceVersion = serviceVersion;
    }
  }

  @PluginBuilderFactory
  public static <B extends StackdriverLayout.Builder<B>> B newBuilder() {
    return new StackdriverLayout.Builder<B>().asBuilder();
  }

  @Override
  public byte[] toByteArray(final LogEvent event) {
    return toSerializable(event);
  }

  @Override
  public byte[] toSerializable(final LogEvent event) {
    final var entry = buildLogEntry(event);

    try (final var output = new ByteArrayOutputStream()) {
      WRITER.writeValue(output, entry);
      output.write(LINE_SEPARATOR);
      return output.toByteArray();
    } catch (final IOException e) {
      LOGGER.error(e);
      return EMPTY;
    }
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }

  @Override
  public void encode(final LogEvent event, final ByteBufferDestination destination) {
    final var entry = buildLogEntry(event);

    try (final var output = new ByteBufferDestinationOutputStream(destination)) {
      WRITER.writeValue(output, entry);
      output.write(LINE_SEPARATOR);
    } catch (final IOException e) {
      LOGGER.error(e);
    }
  }

  @Override
  public boolean requiresLocation() {
    return false;
  }

  private StackdriverLogEntry buildLogEntry(final LogEvent event) {
    final var builder =
        StackdriverLogEntry.builder()
            .withLevel(event.getLevel())
            .withMessage(event.getMessage().getFormattedMessage())
            .withTime(event.getInstant())
            .withDiagnosticContext(event.getContextData())
            .withThreadId(event.getThreadId())
            .withThreadPriority(event.getThreadPriority())
            .withServiceName(serviceName)
            .withServiceVersion(serviceVersion);

    final var source = event.getSource();
    if (source != null) {
      builder.withSource(source);
    }

    final var thrownProxy = event.getThrownProxy();
    if (thrownProxy != null) {
      builder.withException(thrownProxy);
    }

    final var threadName = event.getThreadName();
    if (threadName != null) {
      builder.withThreadName(threadName);
    }

    final var loggerName = event.getLoggerName();
    if (loggerName != null) {
      builder.withLogger(loggerName);
    }

    return builder.build();
  }

  public static class Builder<B extends StackdriverLayout.Builder<B>>
      extends AbstractLayout.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<StackdriverLayout> {

    @PluginBuilderAttribute("serviceName")
    private String serviceName;

    @PluginBuilderAttribute("serviceVersion")
    private String serviceVersion;

    @Override
    public StackdriverLayout build() {
      return new StackdriverLayout(getConfiguration(), getServiceName(), getServiceVersion());
    }

    public String getServiceName() {
      return serviceName;
    }

    public B setServiceName(final String serviceName) {
      this.serviceName = serviceName;
      return asBuilder();
    }

    public String getServiceVersion() {
      return serviceVersion;
    }

    public B setServiceVersion(final String serviceVersion) {
      this.serviceVersion = serviceVersion;
      return asBuilder();
    }
  }
}
