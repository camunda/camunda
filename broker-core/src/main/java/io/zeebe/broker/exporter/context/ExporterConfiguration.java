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
package io.zeebe.broker.exporter.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.zeebe.broker.exporter.ExporterException;
import io.zeebe.exporter.api.context.Configuration;
import java.util.Map;

public class ExporterConfiguration implements Configuration {
  private static final Gson CONFIG_INSTANTIATOR = new GsonBuilder().create();

  private final String id;
  private final Map<String, Object> arguments;

  private JsonElement intermediateConfiguration;

  public ExporterConfiguration(final String id, final Map<String, Object> arguments) {
    this.id = id;
    this.arguments = arguments;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return arguments;
  }

  @Override
  public <T> T instantiate(Class<T> configClass) {
    if (arguments != null) {
      return CONFIG_INSTANTIATOR.fromJson(getIntermediateConfiguration(), configClass);
    } else {
      try {
        return configClass.newInstance();
      } catch (Exception e) {
        throw new ExporterException(
            "Unable to instantiate config class "
                + configClass.getName()
                + " with default constructor",
            e);
      }
    }
  }

  private JsonElement getIntermediateConfiguration() {
    if (intermediateConfiguration == null) {
      intermediateConfiguration = CONFIG_INSTANTIATOR.toJsonTree(arguments);
    }

    return intermediateConfiguration;
  }
}
