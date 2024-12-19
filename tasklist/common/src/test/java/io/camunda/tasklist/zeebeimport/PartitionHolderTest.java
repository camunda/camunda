/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.zeebe.PartitionHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartitionHolderTest {

  private Optional<List<Integer>> zeebePartitionIds;
  private Optional<List<Integer>> elasticSearchPartitionIds;
  private int slept = 0;

  // Mock the involved components
  private final PartitionHolder partitionHolder =
      new PartitionHolder() {
        @Override
        protected List<Integer> extractCurrentNodePartitions(final List<Integer> partitionIds) {
          return partitionIds;
        }

        @Override
        protected Optional<List<Integer>> getPartitionIdsFromZeebe() {
          return zeebePartitionIds;
        }

        @Override
        protected void sleepFor(final long milliseconds) {
          slept++;
        }
      };

  @BeforeEach
  void setUp() {
    slept = 0;
    zeebePartitionIds = Optional.empty();
    elasticSearchPartitionIds = Optional.empty();
  }

  @Test
  void testGetEmptyPartitionIdsWhenNoComponentAvailable() {
    zeebePartitionIds = Optional.empty();
    elasticSearchPartitionIds = Optional.empty();

    Assertions.assertThat(partitionHolder.getPartitionIds()).isEmpty();
    Assertions.assertThat(slept).isEqualTo(PartitionHolder.MAX_RETRY);
  }

  @Test
  void testGetCamundaClientPartitionIds() {
    zeebePartitionIds = Optional.of(new ArrayList<>(CollectionUtil.fromTo(5, 10)));
    elasticSearchPartitionIds = Optional.empty();

    Assertions.assertThat(partitionHolder.getPartitionIds())
        .isNotEmpty()
        .contains(5, 6, 7, 8, 9, 10);
    Assertions.assertThat(slept).isEqualTo(0);
  }

  @Test
  void testGetPartitionIds() {
    zeebePartitionIds = Optional.of(new ArrayList<>(CollectionUtil.fromTo(1, 5)));
    elasticSearchPartitionIds = Optional.of(new ArrayList<>(CollectionUtil.fromTo(1, 5)));

    Assertions.assertThat(partitionHolder.getPartitionIds()).isNotEmpty().contains(1, 2, 3, 4, 5);
    Assertions.assertThat(slept).isEqualTo(0);
  }

  @Test
  void testGetPartitionIdsWithDifferentSets() {
    zeebePartitionIds = Optional.of(new ArrayList<>(CollectionUtil.fromTo(1, 7)));
    elasticSearchPartitionIds = Optional.of(new ArrayList<>(CollectionUtil.fromTo(1, 5)));

    Assertions.assertThat(partitionHolder.getPartitionIds()).containsAll(zeebePartitionIds.get());
    Assertions.assertThat(slept).isEqualTo(0);
  }
}
