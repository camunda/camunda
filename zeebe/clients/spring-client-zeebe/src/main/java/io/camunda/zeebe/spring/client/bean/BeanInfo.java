package io.camunda.zeebe.spring.client.bean;

import static org.springframework.util.ReflectionUtils.getAllDeclaredMethods;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.aop.support.AopUtils;

public interface BeanInfo {

  static Supplier<IllegalStateException> noAnnotationFound(final Class<? extends Annotation> type) {
    return () -> new IllegalStateException("no annotation found - " + type);
  }

  Object getBean();

  String getBeanName();

  default Class<?> getTargetClass() {
    return AopUtils.getTargetClass(getBean());
  }

  default boolean hasClassAnnotation(final Class<? extends Annotation> type) {
    return getTargetClass().isAnnotationPresent(type);
  }

  default boolean hasMethodAnnotation(final Class<? extends Annotation> type) {
    return Stream.of(getAllDeclaredMethods(getTargetClass()))
        .anyMatch(m -> m.isAnnotationPresent(type));
  }
}
