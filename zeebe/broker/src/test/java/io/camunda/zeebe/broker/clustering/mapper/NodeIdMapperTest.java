/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.clustering.mapper.lease.LeaseClient.Lease;
import io.camunda.zeebe.broker.clustering.mapper.lease.NodeIdMappings;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class NodeIdMapperTest {

  @Nested
  class MappingsReadiness {

    // all nodes have id 2 -> version 3
    private static final List<Lease> EXAMPLE_MAPPINGS =
        List.of(
            new Lease(
                "1",
                1L,
                new NodeInstance(1, 2),
                new NodeIdMappings(Map.of("1", 2, "2", 3, "3", 3))),
            new Lease(
                "2",
                1L,
                new NodeInstance(2, 2),
                new NodeIdMappings(Map.of("1", 1, "2", 3, "3", 3))),
            new Lease("3", 1L, new NodeInstance(3, 3)));

    private static final List<Lease> EXTRA_OUTDATED_MAPPINGS =
        List.of(
            new Lease(
                "1",
                1L,
                new NodeInstance(1, 2),
                new NodeIdMappings(Map.of("1", 2, "2", 3, "3", 2))));

    @Test
    public void allMappingsAreReady() {
      assertThat(NodeIdMapper.allMappingsAreUpdated(EXAMPLE_MAPPINGS, new NodeInstance(3, 3), 3))
          .isTrue();
    }

    @Test
    public void oneMappingIsMissing() {
      assertThat(NodeIdMapper.allMappingsAreUpdated(EXAMPLE_MAPPINGS, new NodeInstance(1, 2), 3))
          .isFalse();
    }

    @Test
    public void notAllMappingsAreReady() {
      assertThat(
              NodeIdMapper.allMappingsAreUpdated(
                  Stream.concat(
                          // skip the first entry from the valid mappings
                          EXAMPLE_MAPPINGS.stream().filter(l -> !l.taskId().equals("1")),
                          EXTRA_OUTDATED_MAPPINGS.stream())
                      .toList(),
                  new NodeInstance(3, 3),
                  3))
          .isFalse();
    }
  }
}
