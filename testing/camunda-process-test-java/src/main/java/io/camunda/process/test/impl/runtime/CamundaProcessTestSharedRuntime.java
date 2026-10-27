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

import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.net.URI;
import java.time.Duration;
import java.util.function.Supplier;

public class CamundaProcessTestSharedRuntime implements CamundaProcessTestRuntime {

  private final SharedRuntimeStorage sharedRuntimeStorage;

  public CamundaProcessTestSharedRuntime(
      final Supplier<CamundaProcessTestContainerRuntime> runtimeBuilder) {
    this(runtimeBuilder, new StaticSharedRuntimeStorage());
  }

  CamundaProcessTestSharedRuntime(
      final Supplier<CamundaProcessTestContainerRuntime> runtimeBuilder,
      final SharedRuntimeStorage sharedRuntimeStorage) {
    this.sharedRuntimeStorage = sharedRuntimeStorage;
    initializeSharedRuntime(runtimeBuilder, sharedRuntimeStorage);
  }

  private static synchronized void initializeSharedRuntime(
      final Supplier<CamundaProcessTestContainerRuntime> runtimeBuilder,
      final SharedRuntimeStorage sharedRuntimeStorage) {

    CamundaProcessTestContainerRuntime sharedRuntime = sharedRuntimeStorage.getRuntime();
    if (sharedRuntime == null) {
      // Initialize the shared runtime only once, when the first test class is instantiated. The
      // next test classes will reuse the same runtime instance.
      sharedRuntime = runtimeBuilder.get();
      sharedRuntimeStorage.setRuntime(sharedRuntime);

      // Register a shutdown hook to close the shared runtime when all tests are finished.
      registerClosingRuntimeHook(sharedRuntime);
    }
  }

  @Override
  public void start() {
    startSharedRuntime();
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return getSharedRuntime().getCamundaRestApiAddress();
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return getSharedRuntime().getCamundaGrpcApiAddress();
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return getSharedRuntime().getCamundaMonitoringApiAddress();
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    return getSharedRuntime().getConnectorsRestApiAddress();
  }

  @Override
  public CamundaClientBuilderFactory getCamundaClientBuilderFactory() {
    return getSharedRuntime().getCamundaClientBuilderFactory();
  }

  @Override
  public Topology waitUntilClusterReady(final Duration timeout) {
    return getSharedRuntime().waitUntilClusterReady(timeout);
  }

  private synchronized void startSharedRuntime() {
    if (!getSharedRuntime().isStarted()) {
      // Start the shared runtime only once, when the first test class is instantiated. The next
      // test classes will reuse the same runtime instance, which is already started.
      getSharedRuntime().start();
    }
  }

  private static void registerClosingRuntimeHook(
      final CamundaProcessTestContainerRuntime sharedRuntime) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    if (sharedRuntime.isStarted()) {
                      sharedRuntime.close();
                    }
                  } catch (final Exception e) {
                    throw new RuntimeException("Failed to close shared runtime", e);
                  }
                }));
  }

  private CamundaProcessTestContainerRuntime getSharedRuntime() {
    return sharedRuntimeStorage.getRuntime();
  }

  @Override
  public void close() throws Exception {
    // Do not close the shared runtime here, as it is shared across multiple test classes.
  }

  private static final class StaticSharedRuntimeStorage implements SharedRuntimeStorage {

    private static CamundaProcessTestContainerRuntime sharedRuntime;

    @Override
    public CamundaProcessTestContainerRuntime getRuntime() {
      return sharedRuntime;
    }

    @Override
    public void setRuntime(final CamundaProcessTestContainerRuntime runtime) {
      sharedRuntime = runtime;
    }
  }

  /**
   * Internal interface to abstract the storage of the shared runtime instance. This allows us to
   * inject a mock implementation for testing purposes.
   */
  interface SharedRuntimeStorage {
    CamundaProcessTestContainerRuntime getRuntime();

    void setRuntime(CamundaProcessTestContainerRuntime runtime);
  }
}
