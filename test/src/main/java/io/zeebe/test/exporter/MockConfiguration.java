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
package io.zeebe.test.exporter;

import io.zeebe.exporter.api.context.Configuration;
import java.util.Map;

/** A mock implementation of {@link Configuration} providing easy control over all properties. */
public class MockConfiguration<T> implements Configuration {

  private String id;
  private Map<String, Object> arguments;
  private T configuration;

  public MockConfiguration() {}

  /** @param configuration will be returned every time by a call to {{@link #instantiate(Class)}} */
  public MockConfiguration(T configuration) {
    this.configuration = configuration;
  }

  public MockConfiguration(String id, Map<String, Object> arguments) {
    this.id = id;
    this.arguments = arguments;
  }

  public MockConfiguration(String id, Map<String, Object> arguments, T configuration) {
    this.id = id;
    this.arguments = arguments;
    this.configuration = configuration;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return arguments;
  }

  public void setArguments(Map<String, Object> arguments) {
    this.arguments = arguments;
  }

  @Override
  public <R> R instantiate(Class<R> configClass) {
    if (configuration != null && configClass.isAssignableFrom(configuration.getClass())) {
      return configClass.cast(configuration);
    }

    return null;
  }

  /**
   * Sets a fixed return value for {@link #instantiate(Class)}
   *
   * @param configuration instance to return for {@link #instantiate(Class)} calls
   */
  public void setConfiguration(T configuration) {
    this.configuration = configuration;
  }
}
