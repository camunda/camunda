/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.Protocol;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class KeyGeneratorTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private KeyGenerator keyGenerator;

  @Before
  public void setUp() throws Exception {
    keyGenerator = stateRule.getZeebeState().getKeyGenerator();
  }

  @Test
  public void shouldGetFirstValue() {
    // given

    // when
    final long firstKey = keyGenerator.nextKey();

    // then
    assertThat(firstKey).isEqualTo(Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1));
  }

  @Test
  public void shouldGetNextValue() {
    // given
    final long key = keyGenerator.nextKey();

    // when
    final long nextKey = keyGenerator.nextKey();

    // then
    assertThat(nextKey).isGreaterThan(key);
  }

  @Test
  public void shouldGetUniqueValuesOverPartitions() throws Exception {
    // given
    final ZeebeDb<ZbColumnFamilies> newDb = stateRule.createNewDb();
    final int secondPartitionId = Protocol.DEPLOYMENT_PARTITION + 1;
    final ZeebeState otherZeebeState =
        new ZeebeDbState(secondPartitionId, newDb, newDb.createContext());

    final KeyGenerator keyGenerator2 = otherZeebeState.getKeyGenerator();

    final long keyOfFirstPartition = keyGenerator.nextKey();

    // when
    final long keyOfSecondPartition = keyGenerator2.nextKey();

    // then
    assertThat(keyOfFirstPartition).isNotEqualTo(keyOfSecondPartition);

    assertThat(Protocol.decodePartitionId(keyOfFirstPartition))
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(Protocol.decodePartitionId(keyOfSecondPartition)).isEqualTo(secondPartitionId);

    newDb.close();
  }
}
