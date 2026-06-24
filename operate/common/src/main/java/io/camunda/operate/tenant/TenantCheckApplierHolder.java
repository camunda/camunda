/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.tenant;

import io.camunda.operate.conditions.OpensearchCondition;
import java.util.Optional;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class TenantCheckApplierHolder
    implements ApplicationContextAware, ApplicationListener<ContextClosedEvent> {
  private static ApplicationContext applicationContext;
  private static TenantCheckApplier<Query> tenantCheckApplier;

  public static synchronized Optional<TenantCheckApplier<Query>> getOpenSearchTenantCheckApplier() {
    try {
      if (tenantCheckApplier == null) {
        tenantCheckApplier = applicationContext.getBean(TenantCheckApplier.class);
      }
      return Optional.of(tenantCheckApplier);
    } catch (final NoSuchBeanDefinitionException ex) {
      tenantCheckApplier = null;
      return Optional.empty();
    }
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent ignored) {
    tenantCheckApplier = null;
  }
}
