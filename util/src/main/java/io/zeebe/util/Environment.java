/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class Environment {

  private static final Logger LOG = Loggers.CONFIG_LOGGER;

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

  public Optional<Boolean> getBool(String name) {
    try {
      return get(name).map(Boolean::valueOf);
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
