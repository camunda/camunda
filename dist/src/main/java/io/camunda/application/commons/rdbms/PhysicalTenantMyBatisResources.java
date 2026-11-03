/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.IdlePhysicalTenantResourceReleaser;
import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import io.camunda.application.commons.pt.PhysicalTenantResourceUnavailableException;
import io.camunda.configuration.data.secondarystorage.Rdbms;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
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
  private final IdlePhysicalTenantResourceReleaser releaser;

  private PhysicalTenantMyBatisResources(
      final Map<String, LazyPhysicalTenantResource<PhysicalTenantMyBatis>> myBatisByTenant,
      final RdbmsDataSources dataSources,
      final IdlePhysicalTenantResourceReleaser releaser) {
    this.myBatisByTenant = myBatisByTenant;
    this.dataSources = dataSources;
    this.releaser = releaser;
  }

  /**
   * Prepares every tenant of the data sources, and builds at once the MyBatis of the tenants that
   * opt out of {@link Rdbms#isLazyInitialization() lazy initialization}. A tenant whose MyBatis
   * cannot be built is left to its first use rather than stopping the node. A tenant that nothing
   * uses for its {@link Rdbms#getIdleReleaseTimeout() idle release timeout} gets its MyBatis and
   * its pool closed, until its next use.
   */
  static PhysicalTenantMyBatisResources create(
      final RdbmsDataSources dataSources, final Function<String, Rdbms> configOfTenant) {
    final var byTenant =
        new LinkedHashMap<String, LazyPhysicalTenantResource<PhysicalTenantMyBatis>>();
    final var releasable = new ArrayList<IdlePhysicalTenantResourceReleaser.Entry>();
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
              ignored -> dataSources.releasePool(physicalTenantId),
              RETRY_BACKOFF,
              InstantSource.system());
      byTenant.put(physicalTenantId, myBatis);
      releasable.add(
          new IdlePhysicalTenantResourceReleaser.Entry(myBatis, config.getIdleReleaseTimeout()));
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
    return new PhysicalTenantMyBatisResources(
        Map.copyOf(byTenant),
        dataSources,
        IdlePhysicalTenantResourceReleaser.start("rdbms", releasable));
  }

  public RdbmsMapperBundle mapperBundleFor(final String physicalTenantId) {
    final var myBatis = myBatisOf(physicalTenantId);
    return RdbmsMapperBundle.from(
        new LazySqlSessionFactory(myBatis),
        new LazyMappers(myBatis),
        dataSources.vendorPropertiesFor(physicalTenantId));
  }

  /**
   * A mapper for background work on the tenant, such as metrics: it only runs while the tenant's
   * MyBatis is in use for other work on this node, and does not keep it from being released.
   */
  public <T> T backgroundMapper(final String physicalTenantId, final Class<T> type) {
    return new LazyMappers(myBatisOf(physicalTenantId), true).getMapper(type);
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
    releaser.close();
    myBatisByTenant.values().forEach(LazyPhysicalTenantResource::close);
  }
}
