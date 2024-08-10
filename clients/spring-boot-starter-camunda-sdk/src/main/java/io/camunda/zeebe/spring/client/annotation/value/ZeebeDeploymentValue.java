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
package io.camunda.zeebe.spring.client.annotation.value;

import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.util.List;
import java.util.Objects;

public final class ZeebeDeploymentValue implements ZeebeAnnotationValue<ClassInfo> {

  private final List<String> resources;

  private final ClassInfo beanInfo;

  private ZeebeDeploymentValue(final List<String> resources, final ClassInfo beanInfo) {
    this.resources = resources;
    this.beanInfo = beanInfo;
  }

  public List<String> getResources() {
    return resources;
  }

  @Override
  public ClassInfo getBeanInfo() {
    return beanInfo;
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
    final ZeebeDeploymentValue that = (ZeebeDeploymentValue) o;
    return Objects.equals(resources, that.resources) && Objects.equals(beanInfo, that.beanInfo);
  }

  @Override
  public String toString() {
    return "ZeebeDeploymentValue{" + "resources=" + resources + ", beanInfo=" + beanInfo + '}';
  }

  public static ZeebeDeploymentValueBuilder builder() {
    return new ZeebeDeploymentValueBuilder();
  }

  public static final class ZeebeDeploymentValueBuilder {

    private List<String> resources;
    private ClassInfo beanInfo;

    private ZeebeDeploymentValueBuilder() {}

    public ZeebeDeploymentValueBuilder resources(final List<String> resources) {
      this.resources = resources;
      return this;
    }

    public ZeebeDeploymentValueBuilder beanInfo(final ClassInfo beanInfo) {
      this.beanInfo = beanInfo;
      return this;
    }

    public ZeebeDeploymentValue build() {
      return new ZeebeDeploymentValue(resources, beanInfo);
    }
  }
}
