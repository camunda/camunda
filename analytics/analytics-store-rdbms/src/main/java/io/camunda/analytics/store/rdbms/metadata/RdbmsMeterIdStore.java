/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.meter.MeterIdStore;
import io.camunda.analytics.meter.MeterKey;
import io.camunda.analytics.store.rdbms.metadata.row.MeterIdRow;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The durable {@link MeterIdStore}, backing the {@link io.camunda.analytics.meter.MeterRegistry}'s
 * {@code aggId} allocations via the {@link MeterIdMapper} over {@code ANALYTICS_METER_ID}. Loading
 * the full map on start and persisting each new allocation makes an {@code aggId} stable across
 * restarts and identical across both stages — the property the on-disk rollup and the shuffle
 * depend on.
 */
final class RdbmsMeterIdStore implements MeterIdStore {

  private final SqlSessionFactory sessionFactory;

  RdbmsMeterIdStore(final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Map<MeterKey, Integer> load() {
    final Map<MeterKey, Integer> ids = new HashMap<>();
    try (SqlSession session = sessionFactory.openSession()) {
      for (final MeterIdRow row : session.getMapper(MeterIdMapper.class).selectAll()) {
        ids.put(new MeterKey(row.getCubeId(), row.getMeterName()), row.getAggId());
      }
    }
    return ids;
  }

  @Override
  public void persist(final MeterKey key, final int aggId) {
    try (SqlSession session = sessionFactory.openSession()) {
      session
          .getMapper(MeterIdMapper.class)
          .insert(new MeterIdRow(key.cubeId(), key.meterName(), aggId));
      session.commit();
    }
  }
}
