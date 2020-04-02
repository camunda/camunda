/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.config.impl;

import com.google.common.collect.Maps;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.ConfigService;
import io.atomix.primitive.config.PrimitiveConfig;
import java.util.Collection;
import java.util.Map;

/** Default configuration service. */
public class DefaultConfigService implements ConfigService {
  private final Map<String, PrimitiveConfig> defaultConfigs = Maps.newConcurrentMap();
  private final Map<String, PrimitiveConfig> configs = Maps.newConcurrentMap();

  public DefaultConfigService(
      final Collection<PrimitiveConfig> defaultConfigs, final Collection<PrimitiveConfig> configs) {
    defaultConfigs.forEach(
        config -> this.defaultConfigs.put(((PrimitiveType) config.getType()).name(), config));
    configs.forEach(config -> this.configs.put(config.getName(), config));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C extends PrimitiveConfig<C>> C getConfig(
      final String primitiveName, final PrimitiveType primitiveType) {
    C config = (C) configs.get(primitiveName);
    if (config != null) {
      return config;
    }
    if (primitiveType == null) {
      return null;
    }
    config = (C) defaultConfigs.get(primitiveType.name());
    if (config != null) {
      return config;
    }
    return (C) primitiveType.newConfig();
  }
}
