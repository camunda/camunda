/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import io.camunda.application.commons.pt.PhysicalTenantResourceUnavailableException;
import io.camunda.configuration.data.secondarystorage.Rdbms;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import java.time.Duration;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MyBatis of every physical tenant on this node, each built on its first use, and the mapper
 * bundles that use them. A tenant whose database is never used on this node costs it neither the
 * parsed mapper graph nor any connection.
 */
@NullMarked
public final class PhysicalTenantMyBatisResources implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantMyBatisResources.class);
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

  private final Map<String, LazyPhysicalTenantResource<PhysicalTenantMyBatis>> myBatisByTenant;
  private final RdbmsDataSources dataSources;

  private PhysicalTenantMyBatisResources(
      final Map<String, LazyPhysicalTenantResource<PhysicalTenantMyBatis>> myBatisByTenant,
      final RdbmsDataSources dataSources) {
    this.myBatisByTenant = myBatisByTenant;
    this.dataSources = dataSources;
  }

  /**
   * Prepares every tenant of the data sources, and builds at once the MyBatis of the tenants that
   * opt out of {@link Rdbms#isLazyInitialization() lazy initialization}. A tenant whose MyBatis
   * cannot be built is left to its first use rather than stopping the node.
   */
  static PhysicalTenantMyBatisResources create(
      final RdbmsDataSources dataSources, final Function<String, Rdbms> configOfTenant) {
    final var byTenant =
        new LinkedHashMap<String, LazyPhysicalTenantResource<PhysicalTenantMyBatis>>();
    for (final var physicalTenantId : dataSources.physicalTenantIds()) {
      final var config = configOfTenant.apply(physicalTenantId);
      final var myBatis =
          new LazyPhysicalTenantResource<>(
              physicalTenantId,
              "RDBMS mappers",
              () ->
                  PhysicalTenantMyBatis.build(
                      dataSources.dataSourceFor(physicalTenantId),
                      dataSources.vendorPropertiesFor(physicalTenantId),
                      config.getPrefix()),
              ignored -> {},
              RETRY_BACKOFF,
              InstantSource.system());
      byTenant.put(physicalTenantId, myBatis);
      if (!config.isLazyInitialization()) {
        try {
          myBatis.get();
        } catch (final PhysicalTenantResourceUnavailableException e) {
          LOG.warn(
              "Physical tenant '{}' is served once its RDBMS mappers can be built",
              physicalTenantId);
        }
      }
    }
    return new PhysicalTenantMyBatisResources(Map.copyOf(byTenant), dataSources);
  }

  public RdbmsMapperBundle mapperBundleFor(final String physicalTenantId) {
    final var myBatis = myBatisOf(physicalTenantId);
    return RdbmsMapperBundle.from(
        new LazySqlSessionFactory(myBatis),
        new LazyMappers(myBatis),
        dataSources.vendorPropertiesFor(physicalTenantId));
  }

  LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatisOf(final String physicalTenantId) {
    final var myBatis = myBatisByTenant.get(physicalTenantId);
    if (myBatis == null) {
      throw new IllegalArgumentException(
          "No RDBMS mappers configured for physical tenant " + physicalTenantId);
    }
    return myBatis;
  }

  public boolean isCreated(final String physicalTenantId) {
    final var myBatis = myBatisByTenant.get(physicalTenantId);
    return myBatis != null && myBatis.state() == LazyPhysicalTenantResource.State.CREATED;
  }

  @Override
  public void close() {
    myBatisByTenant.values().forEach(LazyPhysicalTenantResource::close);
  }
}
