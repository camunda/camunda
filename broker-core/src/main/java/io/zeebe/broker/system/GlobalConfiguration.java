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
package io.zeebe.broker.system;

import static io.zeebe.util.FileUtil.createTempDirectory;
import static io.zeebe.util.FileUtil.getCanonicalPath;

import java.io.File;

import io.zeebe.broker.Loggers;
import org.slf4j.Logger;

public class GlobalConfiguration extends DirectoryConfiguration
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

    public static final String GLOBAL_DIRECTORY_DEFAULT = "./data";
    public static final String GLOBAL_DIRECTORY_TEMP = "zeebe-data-";

    public boolean useTempDirectory;
    public boolean standalone;

    public void init()
    {
        if (directory == null || directory.isEmpty())
        {
            if (!useTempDirectory)
            {
                directory = GLOBAL_DIRECTORY_DEFAULT;
            }
            else
            {
                final File tmp = createTempDirectory(GLOBAL_DIRECTORY_TEMP);
                directory = tmp.toString();

            }
        }
        else if (useTempDirectory)
        {
            throw new RuntimeException("Can't use attribute 'directory' and element 'useTempDirectory' together as global configuration, only use one.");
        }

        directory = getCanonicalPath(directory);

        LOG.info("Using data directory: {}", directory);
    }

    public boolean isTempDirectory()
    {
        return useTempDirectory;
    }

}
