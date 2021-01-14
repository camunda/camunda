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
package io.zeebe.client.impl.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of client environment variables
 *
 * <p>Useful primarily to allow us to test with custom environment variables
 */
public final class Environment {
  private final Map<String, String> variables;

  private Environment(final Map<String, String> variables) {
    this.variables = new HashMap<>(variables);
  }

  public static Environment system() {
    return EnvironmentSingleton.SYSTEM;
  }

  public String get(final String key) {
    return variables.getOrDefault(key, System.getenv(key));
  }

  public void put(final String key, final String value) {
    variables.put(key, value);
  }

  public String remove(final String key) {
    return variables.remove(key);
  }

  public boolean isDefined(final String key) {
    return get(key) != null;
  }

  public boolean getBoolean(final String key) {
    return isDefined(key) && get(key).equals("true");
  }

  // primarily for testing
  Map<String, String> copy() {
    return new HashMap<>(variables);
  }

  // primarily for testing
  void overwrite(final Map<String, String> environment) {
    variables.clear();
    variables.putAll(environment);
  }

  // Returns an environment created from the system's environment variables using System#getenv()
  private static class EnvironmentSingleton {
    private static final Environment SYSTEM = new Environment(System.getenv());
  }
}
