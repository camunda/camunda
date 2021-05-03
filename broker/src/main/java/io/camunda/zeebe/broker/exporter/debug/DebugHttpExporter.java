/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.debug;

import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.protocol.record.Record;
import org.slf4j.Logger;

public final class DebugHttpExporter implements Exporter {

  private static DebugHttpServer httpServer;

  private Logger log;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    initHttpServer(context);
  }

  @Override
  public void close() {
    stopHttpServer();
  }

  @Override
  public void export(final Record<?> record) {
    try {
      httpServer.add(record);
    } catch (final Exception e) {
      log.warn("Failed to serialize record {} to json", record, e);
    }
  }

  private synchronized void initHttpServer(final Context context) {
    if (httpServer == null) {
      final DebugHttpExporterConfiguration configuration =
          context.getConfiguration().instantiate(DebugHttpExporterConfiguration.class);

      httpServer = new DebugHttpServer(configuration.getPort(), configuration.getLimit());
      log.info(
          "Debug http server started, inspect the last {} records on http://localhost:{}",
          configuration.getLimit(),
          configuration.getPort());
    }
  }

  public synchronized void stopHttpServer() {
    if (httpServer != null) {
      httpServer.close();
      httpServer = null;
    }
  }

  public static ExporterCfg defaultConfig() {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(DebugHttpExporter.class.getName());
    return exporterCfg;
  }

  public static class DebugHttpExporterConfiguration {

    private int port = 8000;
    private int limit = 1024;

    private int getPort() {
      return port;
    }

    private void setPort(final int port) {
      this.port = port;
    }

    private int getLimit() {
      return limit;
    }

    private void setLimit(final int limit) {
      this.limit = limit;
    }
  }
}
