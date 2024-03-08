package io.camunda.zeebe.spring.client.annotation.value;

import io.camunda.zeebe.spring.client.bean.BeanInfo;

/**
 * Common type for all annotation values.
 *
 * @param <B> either {@link io.camunda.zeebe.spring.client.bean.ClassInfo} or {@link
 *     io.camunda.zeebe.spring.client.bean.MethodInfo}.
 */
public interface ZeebeAnnotationValue<B extends BeanInfo> {

  /**
   * @return the context of this annotation.
   */
  B getBeanInfo();
}
