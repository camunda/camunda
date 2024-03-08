package io.camunda.zeebe.spring.client.bean;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

public class ClassInfo implements BeanInfo {

  private Object bean;
  private String beanName;

  private ClassInfo(Object bean, String beanName) {
    this.bean = bean;
    this.beanName = beanName;
  }

  @Override
  public Object getBean() {
    return bean;
  }

  @Override
  public String getBeanName() {
    return beanName;
  }

  public MethodInfo toMethodInfo(final Method method) {
    return MethodInfo.builder().classInfo(this).method(method).build();
  }

  public <T extends Annotation> Optional<T> getAnnotation(final Class<T> type) {
    return Optional.ofNullable(findAnnotation(getTargetClass(), type));
  }

  public static final ClassInfoBuilder builder() {
    return new ClassInfoBuilder();
  }

  public static final class ClassInfoBuilder {

    private Object bean;
    private String beanName;

    public ClassInfoBuilder() {}

    public ClassInfoBuilder bean(Object bean) {
      this.bean = bean;
      return this;
    }

    public ClassInfoBuilder beanName(String beanName) {
      this.beanName = beanName;
      return this;
    }

    public ClassInfo build() {
      return new ClassInfo(bean, beanName);
    }
  }
}
