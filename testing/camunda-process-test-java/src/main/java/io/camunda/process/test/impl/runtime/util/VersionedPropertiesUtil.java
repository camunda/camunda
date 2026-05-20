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

import io.camunda.process.test.impl.runtime.GitPropertiesUtil;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the Docker image tag for tests using {@code camunda-process-test-java}.
 *
 * <p>The tag is derived from the property value itself (which is filtered from {@code
 * ${project.version}} at build time), so the resolution does not depend on Git branch detection.
 *
 * <ul>
 *   <li>{@code 8.8.10-SNAPSHOT} → {@code 8.8-SNAPSHOT}
 *   <li>{@code 8.10.0-SNAPSHOT} → {@code 8.10-SNAPSHOT}
 *   <li>{@code 8.8.5} (released) → {@code 8.8.5} (used verbatim)
 *   <li>{@code custom-version} → returned verbatim
 * </ul>
 *
 * <p>Unresolved property templates such as {@code
 * ${io.camunda.process.test.camundaDockerImageVersion}} — which appear when the test resources have
 * not been Maven-filtered (e.g. running from IDE without a build) — fall back to {@code
 * defaultValue}.
 */
public class VersionedPropertiesUtil {
  public static final String SNAPSHOT_VERSION = "SNAPSHOT";

  private static final Pattern SEMANTIC_VERSION_PATTERN =
      Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-.*)?");

  /** Format string for snapshot versions (e.g., 8.8-SNAPSHOT). */
  private static final String SNAPSHOT_VERSION_FORMAT = "%d.%d-%s";

  private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

  /**
   * Kept for binary compatibility with existing callers. The git branch is no longer used to
   * resolve the snapshot tag.
   */
  public VersionedPropertiesUtil(final GitPropertiesUtil gitProperties) {
    // gitProperties is no longer consulted; the tag is derived from propertyValue / defaultValue.
  }

  public String getVersion(
      final Properties properties, final String propertyName, final String defaultValue) {
    final String rawValue = properties.getProperty(propertyName);
    final String effectiveValue =
        (rawValue == null || rawValue.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX))
            ? defaultValue
            : rawValue;

    final Matcher matcher = SEMANTIC_VERSION_PATTERN.matcher(effectiveValue);
    if (matcher.find()) {
      final String suffix = matcher.group(4);
      if (suffix != null && suffix.contains(SNAPSHOT_VERSION)) {
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));
        return String.format(SNAPSHOT_VERSION_FORMAT, major, minor, SNAPSHOT_VERSION);
      }
    }
    return effectiveValue;
  }
}
