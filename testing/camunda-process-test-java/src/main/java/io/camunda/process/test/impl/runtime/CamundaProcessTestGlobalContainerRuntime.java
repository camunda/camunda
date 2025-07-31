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

import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestGlobalContainerRuntime implements CamundaProcessTestRuntime {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaProcessTestGlobalContainerRuntime.class);

  private CamundaProcessTestRuntime runtime = null;
  private boolean isRuntimeStarted = false;

  public CamundaProcessTestGlobalContainerRuntime(final CamundaProcessTestRuntime runtime) {
    this.runtime = runtime;
  }

  @Override
  public void start() {
    if (!isRuntimeStarted) {
      LOGGER.info("Starting global CPT container runtime.");
      this.runtime.start();
      isRuntimeStarted = true;
    } else {
      LOGGER.debug("CPT global container runtime already started.");
    }
  }

  @Override
  public void close() {
    LOGGER.debug("Ignoring request to close global Camunda runtime.");
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return runtime.getCamundaRestApiAddress();
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return runtime.getCamundaGrpcApiAddress();
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return runtime.getCamundaMonitoringApiAddress();
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    return runtime.getConnectorsRestApiAddress();
  }

  @Override
  public CamundaClientBuilderFactory getCamundaClientBuilderFactory() {
    return runtime.getCamundaClientBuilderFactory();
  }
}
