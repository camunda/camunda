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
package io.zeebe.broker.system.configuration;

import io.zeebe.broker.Loggers;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class Environment {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final Map<String, String> environment;

  public Environment() {
    this(System.getenv());
  }

  public Environment(Map<String, String> environment) {
    this.environment = environment;
  }

  public Optional<String> get(String name) {
    return Optional.ofNullable(environment.get(name));
  }

  public Optional<Integer> getInt(String name) {
    try {
      return get(name).map(Integer::valueOf);
    } catch (Exception e) {
      LOG.warn("Failed to parse environment variable {}", name, e);
      return Optional.empty();
    }
  }

  public Optional<List<String>> getList(final String name) {
    return get(name)
        .map(v -> v.split(","))
        .map(Arrays::asList)
        .map(
            list ->
                list.stream()
                    .map(String::trim)
                    .filter(e -> !e.isEmpty())
                    .collect(Collectors.toList()));
  }
}
