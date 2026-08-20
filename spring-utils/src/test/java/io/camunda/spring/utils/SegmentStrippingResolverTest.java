/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class SegmentStrippingResolverTest {

  private final ClassPathResource location = new ClassPathResource("segstrip/");

  @Test
  void shouldStripLeadingSegmentsAndResolve() throws IOException {
    // given
    final SegmentStrippingResolver resolver = new SegmentStrippingResolver(2);

    // when
    final Resource result = resolver.getResource("tenant/webapp/leaf.txt", location);

    // then
    assertThat(result).isNotNull();
    assertThat(result.exists()).isTrue();
  }

  @Test
  void shouldReturnNullWhenFewerSegmentsThanSkipCount() throws IOException {
    // given
    final SegmentStrippingResolver resolver = new SegmentStrippingResolver(2);

    // when
    final Resource result = resolver.getResource("leaf.txt", location);

    // then
    assertThat(result).isNull();
  }

  @Test
  void shouldResolveDirectlyWhenSkipIsZero() throws IOException {
    // given
    final SegmentStrippingResolver resolver = new SegmentStrippingResolver(0);

    // when
    final Resource result = resolver.getResource("leaf.txt", location);

    // then
    assertThat(result).isNotNull();
    assertThat(result.exists()).isTrue();
  }

  @Test
  void shouldRejectNegativeSkipCount() {
    // given / when / then
    assertThatThrownBy(() -> new SegmentStrippingResolver(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
