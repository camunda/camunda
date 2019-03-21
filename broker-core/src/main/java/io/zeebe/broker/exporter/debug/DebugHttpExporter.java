/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter.debug;

import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.spi.Exporter;
import org.slf4j.Logger;

public class DebugHttpExporter implements Exporter {

  private static DebugHttpServer httpServer;

  private Logger log;

  @Override
  public void configure(Context context) {
    log = context.getLogger();
    initHttpServer(context);
  }

  private synchronized void initHttpServer(Context context) {
    if (httpServer == null) {
      final DebugHttpExporterConfiguration configuration =
          context.getConfiguration().instantiate(DebugHttpExporterConfiguration.class);

      httpServer = new DebugHttpServer(configuration.port, configuration.limit);
      log.info(
          "Debug http server started, inspect the last {} records on http://localhost:{}",
          configuration.limit,
          configuration.port);
    }
  }

  @Override
  public void export(Record record) {
    try {
      httpServer.add(record);
    } catch (Exception e) {
      log.warn("Failed to serialize record {} to json", record, e);
    }
  }

  @Override
  public void close() {
    stopHttpServer();
  }

  public synchronized void stopHttpServer() {
    if (httpServer != null) {
      httpServer.close();
      httpServer = null;
    }
  }

  public static ExporterCfg defaultConfig() {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setId("http");
    exporterCfg.setClassName(DebugHttpExporter.class.getName());
    return exporterCfg;
  }

  public static class DebugHttpExporterConfiguration {
    public int port = 8000;
    public int limit = 1024;
  }
}
