/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Post-processor that discovers beans with methods annotated with {@link CamundaMcpTool}.
 *
 * <p>This post-processor runs during Spring's bean initialization phase, examining each bean as
 * it's created. Beans containing at least one {@link CamundaMcpTool}-annotated method are
 * registered in the {@link CamundaMcpToolAnnotatedBeans} registry.
 *
 * <p>This approach is preferred over scanning the ApplicationContext because:
 *
 * <ul>
 *   <li>It runs at the correct time in Spring's lifecycle
 *   <li>It only examines each bean once as it's created (more efficient)
 *   <li>It properly handles AOP proxies
 *   <li>It follows the same pattern used by Spring AI for @McpTool discovery
 * </ul>
 */
public class CamundaMcpToolBeanPostProcessor implements BeanPostProcessor {

  private final CamundaMcpToolAnnotatedBeans registry;

  public CamundaMcpToolBeanPostProcessor(final CamundaMcpToolAnnotatedBeans registry) {
    this.registry = registry;
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    // Handle proxied beans by getting the target class
    final Class<?> beanClass = AopUtils.getTargetClass(bean);

    if (hasCamundaMcpToolMethod(beanClass)) {
      registry.addToolBean(bean);
    }

    return bean;
  }

  /**
   * Checks if the given class has any methods annotated with {@link CamundaMcpTool}.
   *
   * @param beanClass the class to scan
   * @return true if at least one method has the annotation
   */
  private boolean hasCamundaMcpToolMethod(final Class<?> beanClass) {
    final boolean[] found = {false};

    ReflectionUtils.doWithMethods(
        beanClass,
        method -> {
          if (AnnotationUtils.findAnnotation(method, CamundaMcpTool.class) != null) {
            found[0] = true;
          }
        });

    return found[0];
  }
}
