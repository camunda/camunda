package io.camunda.zeebe.spring.client.annotation.value;

import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.util.List;
import java.util.Objects;

public class ZeebeDeploymentValue implements ZeebeAnnotationValue<ClassInfo> {

  private List<String> resources;

  private ClassInfo beanInfo;

  private ZeebeDeploymentValue(List<String> resources, ClassInfo beanInfo) {
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ZeebeDeploymentValue that = (ZeebeDeploymentValue) o;
    return Objects.equals(resources, that.resources) && Objects.equals(beanInfo, that.beanInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resources, beanInfo);
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

    public ZeebeDeploymentValueBuilder resources(List<String> resources) {
      this.resources = resources;
      return this;
    }

    public ZeebeDeploymentValueBuilder beanInfo(ClassInfo beanInfo) {
      this.beanInfo = beanInfo;
      return this;
    }

    public ZeebeDeploymentValue build() {
      return new ZeebeDeploymentValue(resources, beanInfo);
    }
  }
}
