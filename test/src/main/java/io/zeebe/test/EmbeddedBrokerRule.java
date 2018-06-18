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
package io.zeebe.test;

import io.zeebe.broker.Broker;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;

public class EmbeddedBrokerRule extends ExternalResource {
  public static final Supplier<InputStream> DEFAULT_CONFIG_SUPPLIER =
      () -> EmbeddedBrokerRule.class.getResourceAsStream("/zeebe.default.cfg.toml");
  private Broker broker;
  private Supplier<InputStream> configSupplier;
  private File brokerBase;

  public EmbeddedBrokerRule() {
    this(DEFAULT_CONFIG_SUPPLIER);
  }

  public EmbeddedBrokerRule(final Supplier<InputStream> configSupplier) {
    this.configSupplier = configSupplier;
  }

  @Override
  protected void before() {
    startBroker();
  }

  @Override
  protected void after() {
    try {
      broker.close();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    } finally {
      try {
        FileUtil.deleteFolder(brokerBase.getAbsolutePath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    broker = null;
    System.gc();
  }

  public void startBroker() {
    try (InputStream configStream = configSupplier.get()) {
      if (brokerBase == null) {
        brokerBase = Files.newTemporaryFolder();
      }

      broker = new Broker(configStream, brokerBase.getAbsolutePath(), null);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to read configuration", e);
    }

    // wait until up and running
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException e) {
      // ignore
    }
  }
}
