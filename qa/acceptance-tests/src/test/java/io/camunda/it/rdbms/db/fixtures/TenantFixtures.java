/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
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

  public static void createAndSaveRandomTenants(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomTenants(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomTenants(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getTenantWriter().create(TenantFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static TenantDbModel createAndSaveTenant(final RdbmsWriter rdbmsWriter) {
    final var instance = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenants(rdbmsWriter, List.of(instance));
    return instance;
  }

  public static TenantDbModel createAndSaveTenant(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var instance = TenantFixtures.createRandomized(builderFunction);
    createAndSaveTenants(rdbmsWriter, List.of(instance));
    return instance;
  }

  public static void createAndSaveTenant(
      final RdbmsWriter rdbmsWriter, final TenantDbModel tenant) {
    createAndSaveTenants(rdbmsWriter, List.of(tenant));
  }

  public static void createAndSaveTenants(
      final RdbmsWriter rdbmsWriter, final List<TenantDbModel> tenantList) {
    for (final TenantDbModel tenant : tenantList) {
      rdbmsWriter.getTenantWriter().create(tenant);
    }
    rdbmsWriter.flush();
  }
}
