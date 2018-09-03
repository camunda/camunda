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
package io.zeebe.broker.exporter;

import io.zeebe.broker.exporter.record.RecordImpl;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.context.Context;
import io.zeebe.exporter.context.Controller;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.spi.Exporter;
import java.util.Collections;
import org.slf4j.Logger;

public class DebugExporter implements Exporter {

  private Logger log;
  private LogLevel logLevel;
  private DebugExporterConfiguration configuration;

  @Override
  public void configure(Context context) {
    log = context.getLogger();
    configuration = context.getConfiguration().instantiate(DebugExporterConfiguration.class);
    logLevel = configuration.getLogLevel();
  }

  @Override
  public void open(Controller controller) {
    log("Debug exporter opened");
  }

  @Override
  public void close() {
    log("Debug exporter closed");
  }

  @Override
  public void export(Record record) {
    if (configuration.prettyPrint) {
      ((RecordImpl) record).getObjectMapper().setPrettyPrint();
    }
    log("{}", record.toJson());
  }

  public static ExporterCfg defaultConfig(final boolean prettyPrint) {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setId("debug");
    exporterCfg.setClassName(DebugExporter.class.getName());
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
