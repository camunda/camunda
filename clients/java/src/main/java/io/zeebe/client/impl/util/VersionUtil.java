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

import io.zeebe.client.impl.Loggers;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;

public final class VersionUtil {

  public static final Logger LOG = Loggers.LOGGER;

  private static final String VERSION_PROPERTIES_PATH = "/client-java.properties";
  private static final String VERSION_PROPERTY_NAME = "zeebe.version";
  private static final String VERSION_DEV = "development";

  private static String version;

  private VersionUtil() {}

  /** @return the current version or 'development' if none can be determined. */
  public static String getVersion() {
    if (version == null) {
      // read version from file
      version = readProperty(VERSION_PROPERTY_NAME);
      if (version == null) {
        LOG.warn("Version is not found in version file.");
        version = VersionUtil.class.getPackage().getImplementationVersion();
      }

      if (version == null) {
        version = VERSION_DEV;
      }
    }

    return version;
  }

  private static String readProperty(final String property) {
    try (final InputStream versionFileStream =
        VersionUtil.class.getResourceAsStream(VERSION_PROPERTIES_PATH)) {
      final Properties props = new Properties();
      props.load(versionFileStream);

      return props.getProperty(property);
    } catch (final IOException e) {
      LOG.error(String.format("Can't read version file: %s", VERSION_PROPERTIES_PATH), e);
    }

    return null;
  }
}
