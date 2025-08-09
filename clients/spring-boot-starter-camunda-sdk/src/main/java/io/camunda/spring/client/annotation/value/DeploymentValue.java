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
package io.camunda.spring.client.annotation.value;

import io.camunda.spring.client.bean.ClassInfo;
import java.util.List;
import java.util.Objects;

public final class DeploymentValue {

  private final List<String> resources;

  private final ClassInfo beanInfo;

  private DeploymentValue(final List<String> resources, final ClassInfo beanInfo) {
    this.resources = resources;
    this.beanInfo = beanInfo;
  }

  public List<String> getResources() {
    return resources;
  }

  @Override
  public int hashCode() {
    return Objects.hash(resources, beanInfo);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentValue that = (DeploymentValue) o;
    return Objects.equals(resources, that.resources) && Objects.equals(beanInfo, that.beanInfo);
  }

  @Override
  public String toString() {
    return "DeploymentValue{" + "resources=" + resources + ", beanInfo=" + beanInfo + '}';
  }

  public static DeploymentValueBuilder builder() {
    return new DeploymentValueBuilder();
  }

  public static final class DeploymentValueBuilder {

    private List<String> resources;
    private ClassInfo beanInfo;

    private DeploymentValueBuilder() {}

    public DeploymentValueBuilder resources(final List<String> resources) {
      this.resources = resources;
      return this;
    }

    public DeploymentValueBuilder beanInfo(final ClassInfo beanInfo) {
      this.beanInfo = beanInfo;
      return this;
    }

    public DeploymentValue build() {
      return new DeploymentValue(resources, beanInfo);
    }
  }
}
