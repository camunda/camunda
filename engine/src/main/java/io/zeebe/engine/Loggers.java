/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine;

import io.zeebe.util.ZbLogger;
import org.slf4j.Logger;

public class Loggers {
  public static final Logger STREAM_PROCESSING = new ZbLogger("io.zeebe.broker.logstreams");
  public static final Logger WORKFLOW_REPOSITORY_LOGGER =
      new ZbLogger("io.zeebe.broker.workflow.repository");

  public static final Logger WORKFLOW_PROCESSOR_LOGGER = new ZbLogger("io.zeebe.broker.workflow");

  public static final Logger getExporterLogger(String exporterId) {
    final String loggerName = String.format("io.zeebe.broker.exporter.%s", exporterId);
    return new ZbLogger(loggerName);
  }
}
