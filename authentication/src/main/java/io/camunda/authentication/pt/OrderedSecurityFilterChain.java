/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@link SecurityFilterChain} decorator that also implements {@link Ordered}. Used by {@link
 * PhysicalTenantSecurityChainRegistrar} to attach a precedence value to each programmatically
 * registered chain.
 *
 * <p>Why a decorator instead of {@code @Order} on a bean method: chains here are registered as
 * {@link org.springframework.beans.factory.support.RootBeanDefinition}s with an instance supplier,
 * so there is no annotated factory method for Spring's {@code AnnotationAwareOrderComparator} to
 * read {@code @Order} from. The comparator does, however, honour {@link Ordered} on the bean
 * <i>instance</i> — so wrapping the chain works transparently. {@link
 * org.springframework.security.web.FilterChainProxy} only calls {@link
 * SecurityFilterChain#matches(HttpServletRequest)} and {@link SecurityFilterChain#getFilters()}, so
 * the decorator is functionally indistinguishable from the wrapped chain at request time.
 */
@NullMarked
final class OrderedSecurityFilterChain implements SecurityFilterChain, Ordered {

  private final SecurityFilterChain delegate;
  private final int order;

  OrderedSecurityFilterChain(final SecurityFilterChain delegate, final int order) {
    this.delegate = delegate;
    this.order = order;
  }

  @Override
  public boolean matches(final HttpServletRequest request) {
    return delegate.matches(request);
  }

  @Override
  public List<Filter> getFilters() {
    return delegate.getFilters();
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public String toString() {
    return "OrderedSecurityFilterChain[order=" + order + ", delegate=" + delegate + "]";
  }
}
