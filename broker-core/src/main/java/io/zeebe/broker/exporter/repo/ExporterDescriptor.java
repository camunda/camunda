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
package io.zeebe.broker.exporter.repo;

import io.zeebe.broker.exporter.context.ExporterConfiguration;
import io.zeebe.exporter.api.spi.Exporter;
import java.util.Map;

public class ExporterDescriptor {
  private final ExporterConfiguration configuration;
  private final Class<? extends Exporter> exporterClass;

  public ExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args) {
    this.exporterClass = exporterClass;
    this.configuration = new ExporterConfiguration(id, args);
  }

  public Exporter newInstance() throws ExporterInstantiationException {
    try {
      return exporterClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new ExporterInstantiationException(getId(), e);
    }
  }

  public ExporterConfiguration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return configuration.getId();
  }
}
