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
package io.camunda.process.test.impl.runtime.util;

import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convenience methods for parsing version strings in the shape of MAJOR.MINOR.PATCH-LABEL from a
 * Properties file.
 */
public class VersionedPropertiesUtil {
  public static final String SNAPSHOT_VERSION = "SNAPSHOT";

  private static final Pattern SEMANTIC_VERSION_PATTERN =
      Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-.*)?");

  public String getVersion(
      final Properties properties, final String propertyName, final String defaultValue) {
    return PropertiesUtil.getPropertyOrDefault(
        properties,
        propertyName,
        propertyValue ->
            Optional.of(propertyValue)
                .map(SEMANTIC_VERSION_PATTERN::matcher)
                .filter(Matcher::find)
                .flatMap(matcher -> Optional.ofNullable(matcher.group(4)))
                .filter(label -> label.contains(SNAPSHOT_VERSION))
                .map(snapshotLabel -> defaultValue)
                .orElse(propertyValue),
        defaultValue);
  }
}
