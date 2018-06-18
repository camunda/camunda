/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.configuration;

import com.moandjiezana.toml.Toml;
import io.zeebe.broker.Loggers;
import java.io.File;
import java.io.InputStream;
import org.slf4j.Logger;

public class TomlConfigurationReader {
  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  public BrokerCfg read(String filePath) {
    final File file = new File(filePath);

    LOG.info("Using configuration file " + file.getAbsolutePath());

    return new Toml().read(file).to(BrokerCfg.class);
  }

  public BrokerCfg read(InputStream configStream) {
    LOG.info("Reading configuration from input stream");

    return new Toml().read(configStream).to(BrokerCfg.class);
  }
}
