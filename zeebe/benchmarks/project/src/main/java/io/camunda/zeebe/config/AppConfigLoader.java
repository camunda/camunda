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
package io.camunda.zeebe.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppConfigLoader {
  private static final Logger LOG = LoggerFactory.getLogger(AppConfigLoader.class);

  private AppConfigLoader() {}

  public static AppCfg load() {
    final Config config = ConfigFactory.load().getConfig("app");
    LOG.info("Loading config: {}", config.root().render());
    return ConfigBeanFactory.create(config, AppCfg.class);
  }

  public static AppCfg load(final String path) {
    final Config config = ConfigFactory.load(path).getConfig("app");
    LOG.info("Loading config: {}", config.root().render());
    return ConfigBeanFactory.create(config, AppCfg.class);
  }
}
