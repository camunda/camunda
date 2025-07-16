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
  private static final String VERSION_FORMAT = "%d.%d.%d";

  /**
   * Format string for versions that include additional labels (e.g., alpha, rc). This is used to
   * format semantic versions with non-SNAPSHOT labels.
   */
  private static final String LABELED_VERSION_FORMAT = "%d.%d.%d%s";

  public static String getLatestReleasedVersion(
      final Properties properties, final String propertyName, final String defaultValue) {

    return PropertiesUtil.getPropertyOrDefault(
        properties,
        propertyName,
        propertyValue ->
            Optional.of(propertyValue)
                .map(SEMANTIC_VERSION_PATTERN::matcher)
                .filter(Matcher::find)
                .map(
                    matcher -> {
                      final int major = Integer.parseInt(matcher.group(1));
                      final int minor = Integer.parseInt(matcher.group(2));
                      final int patch = Integer.parseInt(matcher.group(3));
                      final String label = matcher.group(4);
                      return getLatestReleasedVersion(major, minor, patch, label);
                    })
                .orElse(propertyValue),
        defaultValue);
  }

  private static String getLatestReleasedVersion(
      final int major, final int minor, final int patch, final String label) {

    if (label == null) {
      // release version
      return String.format(VERSION_FORMAT, major, minor, patch);
    } else if (!label.contains(SNAPSHOT_VERSION)) {
      // alpha, rc or other labeled version
      return String.format(LABELED_VERSION_FORMAT, major, minor, patch, label);
    } else if (patch == 0) {
      // current dev version
      return SNAPSHOT_VERSION;
    } else {
      // maintenance dev version
      final int previousPatchVersion = patch - 1;
      return String.format(VERSION_FORMAT, major, minor, previousPatchVersion);
    }
  }
}
