/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.util.ReflectUtil;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class RedistributionStageTest {
  @Test
  void shouldMapAllIndexesConsistently() {
    // given
    final var allStages =
        ReflectUtil.implementationsOfSealedInterface(RedistributionStage.class)
            .map(
                stageClass -> {
                  try {
                    return stageClass.getConstructor().newInstance();
                  } catch (final InstantiationException
                      | IllegalAccessException
                      | InvocationTargetException
                      | NoSuchMethodException e) {
                    throw new RuntimeException(e);
                  }
                });

    // then
    Assertions.assertThat(allStages)
        .allSatisfy(
            stage ->
                Assertions.assertThat(stage)
                    .as("Stage " + stage + " should be mapped to an index consistently")
                    .isEqualTo(
                        RedistributionStage.indexToStage(RedistributionStage.stageToIndex(stage))));
  }

  @Test
  void shouldIterateThroughStages() {
    // given
    final var allStages =
        Stream.iterate(new RedistributionStage.Done(), RedistributionStage::nextStage)
            .skip(1)
            .takeWhile(stage -> !(stage instanceof RedistributionStage.Done));

    // then
    Assertions.assertThat(allStages).containsExactly(new RedistributionStage.Deployments());
  }
}
