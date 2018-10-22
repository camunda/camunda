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
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MANAGEMENT_THREADS;

import io.zeebe.util.Environment;
import java.util.Objects;

public class ThreadsCfg {

  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;

  public void init(Environment environment) {
    environment.getInt(ENV_GATEWAY_MANAGEMENT_THREADS).ifPresent(this::setManagementThreads);
  }

  public int getManagementThreads() {
    return managementThreads;
  }

  public ThreadsCfg setManagementThreads(int managementThreads) {
    this.managementThreads = managementThreads;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ThreadsCfg that = (ThreadsCfg) o;
    return managementThreads == that.managementThreads;
  }

  @Override
  public int hashCode() {
    return Objects.hash(managementThreads);
  }

  @Override
  public String toString() {
    return "ThreadsCfg{" + "managementThreads=" + managementThreads + '}';
  }
}
