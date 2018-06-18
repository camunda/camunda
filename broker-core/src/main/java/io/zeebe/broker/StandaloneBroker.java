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
package io.zeebe.broker;

import static java.lang.Runtime.getRuntime;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class StandaloneBroker {
  private static String tempFolder;

  public static void main(String[] args) {
    final Broker broker;

    if (args.length == 1) {
      broker = startBrokerFromConfiguration(args);
    } else {
      broker = startDefaultBrokerInTempDirectory();
    }

    getRuntime()
        .addShutdownHook(
            new Thread("Broker close Thread") {
              @Override
              public void run() {
                try {
                  if (broker != null) {
                    broker.close();
                  }
                } finally {
                  deleteTempDirectory();
                }
              }
            });

    try (Scanner scanner = new Scanner(System.in)) {
      while (scanner.hasNextLine()) {
        final String nextLine = scanner.nextLine();
        if (nextLine.contains("exit")
            || nextLine.contains("close")
            || nextLine.contains("quit")
            || nextLine.contains("halt")
            || nextLine.contains("shutdown")
            || nextLine.contains("stop")) {
          System.exit(0);
        }
      }
    }
  }

  private static Broker startBrokerFromConfiguration(String[] args) {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    return new Broker(args[0], basePath, null);
  }

  private static Broker startDefaultBrokerInTempDirectory() {
    Loggers.SYSTEM_LOGGER.info("No configuration file specified. Using default configuration.");

    try {
      tempFolder = Files.createTempDirectory("zeebe").toAbsolutePath().normalize().toString();

      final BrokerCfg cfg = new BrokerCfg();
      cfg.setBootstrap(1);

      return new Broker(cfg, tempFolder, null);
    } catch (IOException e) {
      throw new RuntimeException("Could not start broker", e);
    }
  }

  private static void deleteTempDirectory() {
    if (tempFolder != null) {
      try {
        FileUtil.deleteFolder(tempFolder);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
