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
package io.camunda.client.bean;

import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BuilderUtil {
  private static final Logger LOG = LoggerFactory.getLogger(BuilderUtil.class);

  private BuilderUtil() {}

  public static <T> T getBuilder(final Class<T> builderType) {
    final ServiceLoader<T> serviceLoader = ServiceLoader.load(builderType);
    final long count = serviceLoader.stream().count();
    if (count == 0) {
      throw new IllegalStateException("No Builders found of type " + builderType.getName());
    } else if (count == 1) {
      return serviceLoader.iterator().next();
    } else {
      final T builder = serviceLoader.iterator().next();
      LOG.warn(
          "Found more than one Builder instance of type {}, returning instance of type {}",
          builderType,
          builder.getClass());
      return builder;
    }
  }
}
