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
package io.zeebe.util;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.io.InputStream;
import org.slf4j.Logger;

public class TomlConfigurationReader {
  public static final Logger LOG = Loggers.CONFIG_LOGGER;

  public static <T> T read(String filePath, Class<T> type) {
    final File file = new File(filePath);

    LOG.info("Reading configuration for class {} from file {} ", type, file.getAbsolutePath());

    return new Toml().read(file).to(type);
  }

  public static <T> T read(InputStream configStream, Class<T> type) {
    LOG.info("Reading configuration for class {} from input stream", type);

    return new Toml().read(configStream).to(type);
  }
}
