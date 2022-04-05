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
package io.camunda.zeebe.exporter.test;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.camunda.zeebe.exporter.api.context.Configuration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.jcip.annotations.Immutable;

/**
 * An immutable implementation of {@link Configuration}. Accepts configuration suppliers, passing
 * the arguments map to the supplier. This allows for flexible injection of configuration when
 * testing the exporter.
 *
 * @param <T> the actual configuration type
 */
@Immutable
final class ExporterTestConfiguration<T> implements Configuration {
  private final String id;
  private final Map<String, Object> arguments;
  private final Function<Map<String, Object>, T> configurationSupplier;

  ExporterTestConfiguration(final String id, @Nullable final T configuration) {
    this(id, ignored -> configuration);
  }

  ExporterTestConfiguration(
      final String id, final Function<Map<String, Object>, T> configurationSupplier) {
    this(id, Collections.emptyMap(), configurationSupplier);
  }

  public ExporterTestConfiguration(
      final String id,
      final Map<String, Object> arguments,
      final Function<Map<String, Object>, T> configurationSupplier) {
    this.id = Objects.requireNonNull(id, "must specify an ID");
    this.arguments = Objects.requireNonNull(arguments, "must specify arguments");
    this.configurationSupplier =
        Objects.requireNonNull(configurationSupplier, "must specific a configurationSupplier");
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
  public <R> R instantiate(final Class<R> configClass) {
    Objects.requireNonNull(configClass, "must pass a non null configClass");

    final var configuration = configurationSupplier.apply(arguments);
    return configClass.cast(configuration);
  }
}
