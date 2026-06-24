/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.TenantDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class TenantFixtures extends CommonFixtures {

  private TenantFixtures() {}

  public static TenantDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var key = nextKey();
    final var tenantId = nextStringId();
    final var builder = new Builder().tenantKey(key).tenantId(tenantId).name("Tenant " + key);
    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomTenants(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomTenants(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomTenants(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    createAndSaveRandomTenants(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomTenants(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getTenantWriter().create(TenantFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static TenantDbModel createAndSaveTenant(final RdbmsWriters rdbmsWriters) {
    final var instance = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenants(rdbmsWriters, List.of(instance));
    return instance;
  }

  public static TenantDbModel createAndSaveTenant(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var instance = TenantFixtures.createRandomized(builderFunction);
    createAndSaveTenants(rdbmsWriters, List.of(instance));
    return instance;
  }

  public static void createAndSaveTenant(
      final RdbmsWriters rdbmsWriters, final TenantDbModel tenant) {
    createAndSaveTenants(rdbmsWriters, List.of(tenant));
  }

  public static void createAndSaveTenants(
      final RdbmsWriters rdbmsWriters, final List<TenantDbModel> tenantList) {
    for (final TenantDbModel tenant : tenantList) {
      rdbmsWriters.getTenantWriter().create(tenant);
    }
    rdbmsWriters.flush();
  }
}
