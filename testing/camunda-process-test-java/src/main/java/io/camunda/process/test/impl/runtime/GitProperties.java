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
package io.camunda.process.test.impl.runtime;

import io.camunda.process.test.impl.runtime.util.PropertiesUtil;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Contains git-specific information. */
public final class GitProperties {

  public static final GitProperties INSTANCE = new GitProperties();

  public static final String PROPERTY_NAME_GIT_BRANCH = "git.branch";

  private static final Logger LOGGER = LoggerFactory.getLogger(GitProperties.class);

  private static final String GIT_PROPERTIES_FILE = "git.properties";

  /** Current branch */
  private final String branch;

  private GitProperties() {
    this(readGitProperties(""));
  }

  GitProperties(final Properties properties) {
    branch = PropertiesUtil.getPropertyOrDefault(properties, PROPERTY_NAME_GIT_BRANCH, "");
  }

  static GitProperties readProperties(final String directoryOverride) {
    return new GitProperties(readGitProperties(directoryOverride));
  }

  private static Properties readGitProperties(final String dir) {
    final String filePath = dir + GIT_PROPERTIES_FILE;

    try (final InputStream gitPropertiesFileStream =
        GitProperties.class.getClassLoader().getResourceAsStream(filePath)) {

      final Properties properties = new Properties();
      properties.load(gitPropertiesFileStream);

      return properties;
    } catch (final Throwable t) {
      LOGGER.warn("Can't read properties file: {}. Skipping.", filePath, t);

      final Properties properties = new Properties();
      properties.put(PROPERTY_NAME_GIT_BRANCH, "");

      return properties;
    }
  }

  public String getBranch() {
    return branch;
  }
}
