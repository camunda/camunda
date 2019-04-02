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
package io.zeebe.broker.exporter.util;

import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.spi.Exporter;

public class PojoConfigurationExporter implements Exporter {

  public static PojoExporterConfiguration configuration;

  @Override
  public void configure(Context context) {
    configuration = context.getConfiguration().instantiate(PojoExporterConfiguration.class);
  }

  public PojoExporterConfiguration getConfiguration() {
    return configuration;
  }

  @Override
  public void export(Record record) {}

  public class PojoExporterConfiguration {

    public String foo;
    public int x;
    public PojoExporterConfigurationPart nested;
  }

  public class PojoExporterConfigurationPart {
    public String bar;
    public double y;
  }
}
