/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantBinderTest {

  @Test
  void bindingIntoExistingStubInstancePreservesUntouchedNestedFields() {
    // given a stub graph with non-default values, mirroring "root resolved"
    final Holder holder = new Holder();
    holder.getNested().setA(10);
    holder.getNested().setB(20);

    // and properties that override only one nested field under a tenant prefix
    final MockEnvironment env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("prefix.nested.a", 99)));

    // when binding the tenant prefix into the existing instance
    Binder.get(env).bind("prefix", Bindable.ofInstance(holder));

    // then the overridden field is updated and the sibling field is preserved
    assertThat(holder.getNested().getA()).isEqualTo(99);
    assertThat(holder.getNested().getB()).isEqualTo(20);
  }

  @Test
  void bindingTenantPrefixIntoCamundaPreservesUntouchedNestedFields() {
    // given a Camunda instance with non-default cluster fields (root resolved)
    final Camunda root = new Camunda();
    root.getCluster().setSize(5);
    root.getCluster().setReplicationFactor(3);
    root.getCluster().setPartitionCount(7);

    // and properties that override only one nested field for tenant tenanta
    final MockEnvironment env = new MockEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test", Map.of("camunda.physical-tenants.tenanta.cluster.partition-count", 2)));

    // when binding the tenant prefix into the existing Camunda
    Binder.get(env).bind("camunda.physical-tenants.tenanta", Bindable.ofInstance(root));

    // then the overridden field is updated and the sibling cluster fields are preserved
    assertThat(root.getCluster().getPartitionCount()).isEqualTo(2);
    assertThat(root.getCluster().getSize()).isEqualTo(5);
    assertThat(root.getCluster().getReplicationFactor()).isEqualTo(3);
  }

  @Test
  void bindingShardsCountPerIndexPreservesUntouchedExistingKeys() {
    final Camunda root = new Camunda();
    root.getData()
        .getSecondaryStorage()
        .getElasticsearch()
        .setNumberOfShardsPerIndex(Map.of("list-view", 3, "variable", 5));

    final MockEnvironment env = new MockEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.number-of-shards-per-index.list-view",
                    7)));

    Binder.get(env).bind("camunda.physical-tenants.tenanta", Bindable.ofInstance(root));
    assertThat(root.getData().getSecondaryStorage().getElasticsearch().getNumberOfShardsPerIndex())
        .hasFieldOrPropertyWithValue("list-view", 7)
        .hasFieldOrPropertyWithValue("variable", 5);
  }

  public static class Holder {
    private Nested nested = new Nested();

    public Nested getNested() {
      return nested;
    }

    public void setNested(final Nested nested) {
      this.nested = nested;
    }
  }

  public static class Nested {
    private int a;
    private int b;

    public int getA() {
      return a;
    }

    public void setA(final int a) {
      this.a = a;
    }

    public int getB() {
      return b;
    }

    public void setB(final int b) {
      this.b = b;
    }
  }
}
