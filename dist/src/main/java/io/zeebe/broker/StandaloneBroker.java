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
package io.zeebe.broker;

import static java.lang.Runtime.getRuntime;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public class StandaloneBroker {
  private static String tempFolder;

  private static final CountDownLatch WAITING_LATCH = new CountDownLatch(1);

  public static void main(final String[] args) throws Exception {
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
    WAITING_LATCH.await();
  }

  private static Broker startBrokerFromConfiguration(final String[] args) {
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
      return new Broker(cfg, tempFolder, null);
    } catch (final IOException e) {
      throw new RuntimeException("Could not start broker", e);
    }
  }

  private static void deleteTempDirectory() {
    if (tempFolder != null) {
      try {
        FileUtil.deleteFolder(tempFolder);
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }
  }
}
