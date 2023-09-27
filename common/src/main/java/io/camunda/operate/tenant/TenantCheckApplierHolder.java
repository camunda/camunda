/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.tenant;

import io.camunda.operate.conditions.OpensearchCondition;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Conditional(OpensearchCondition.class)
@Component
public class TenantCheckApplierHolder implements ApplicationContextAware {
  private static ApplicationContext applicationContext;
  private static TenantCheckApplier<Query> tenantCheckApplier;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  public static Optional<TenantCheckApplier<Query>> getOpenSearchTenantCheckApplier() {
    if (tenantCheckApplier == null) {
      synchronized (TenantCheckApplierHolder.class) {
        if (tenantCheckApplier == null) {
          try {
            tenantCheckApplier = applicationContext.getBean(TenantCheckApplier.class);
          } catch (NoSuchBeanDefinitionException ex) {
            tenantCheckApplier = null;
          }
        }
      }
    }
    if (tenantCheckApplier == null) {
      return Optional.empty();
    } else {
      return Optional.of(tenantCheckApplier);
    }
  }
}
