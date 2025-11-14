/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class KeyGeneratorTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private KeyGenerator keyGenerator;

  @Before
  public void setUp() throws Exception {
    keyGenerator = stateRule.getProcessingState().getKeyGenerator();
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
    final KeyGenerator keyGenerator2 =
        new DbKeyGenerator(secondPartitionId, newDb, newDb.createContext());

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

  @Test
  public void shouldFailIfKeyOfAnotherPartitionIsSet() {
    // given
    final ZeebeDb<ZbColumnFamilies> newDb = stateRule.createNewDb();
    final DbKeyGenerator keyGenerator2 =
        new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, newDb, newDb.createContext());

    final long keyOfAnotherPartition =
        Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION + 1, 100);

    // when - then
    assertThatException()
        .isThrownBy(() -> keyGenerator2.setKeyIfHigher(keyOfAnotherPartition))
        .havingCause()
        .withMessageContaining(
            String.format(
                "Provided key %d does not belong to partition %d",
                keyOfAnotherPartition, Protocol.DEPLOYMENT_PARTITION));
  }
}
