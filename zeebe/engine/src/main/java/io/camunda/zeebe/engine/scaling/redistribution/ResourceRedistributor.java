/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionProgress;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.SequencedCollection;
import java.util.Set;

/**
 * A {@link ResourceRedistributor} is responsible for producing one or multiple {@link
 * Redistribution}. It is up to the implementation to decide whether it wants to do one
 * redistribution at a time or several at once. This is mostly a performance consideration.
 *
 * <p>A redistribution is a command that contains one or many resources. It will be sent via {@link
 * io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior} such that a matching
 * processor creates all the contained resources.
 *
 * <p>We allow each redistribution to contain multiple resources to support resource nesting as done
 * for Deployments. Each contained resource must be mentioned by its natural key. This allows the
 * {@link RedistributionBehavior} to track progress for each resource independently and not just on
 * a per-redistribution basis, which is necessary to support resources that can be modified outside
 * of their "parent" resource. For example, we want to redistribute entire deployments at once but
 * need to know which contained resources were already redistributed or not to support resource
 * deletion.
 *
 * @param <S> Marker type to associate this redistributor to a specific stage
 * @param <V> The specific type of record values that get redistributed
 */
public interface ResourceRedistributor<
    S extends RedistributionStage, V extends UnifiedRecordValue> {

  /**
   * Is called multiple times by the {@link RedistributionBehavior}. Implementations should find the
   * next redistributions and return them
   *
   * @param progress The recorded progress until now. This contains resource keys that were already
   *     redistributed.
   * @return A sequence of {@link Redistribution} or an empty sequence to indicate that this
   *     redistributor is done, and we can move on to the next {@link RedistributionStage}.
   */
  SequencedCollection<Redistribution<V>> nextRedistributions(RedistributionProgress progress);

  /**
   * Contains all data necessary to redistribute one or several resources.
   *
   * @param distributionKey The key that will be used for distributing. Some resources require that
   *     they are distributed with a specific key, some don't care and can deal with a newly
   *     generated key.
   * @param distributionValueType The value type of the distribution command.
   * @param distributionIntent The intent of the distribution command.
   * @param distributionValue The actual value of the distribution command.
   * @param containedResources A set of {@link RedistributableResource} that are contained in this
   *     redistribution. Some redistributions might contain many resources. For example, deployments
   *     get redistributed as one but each contains many resources of different types.
   * @param <V> The implementation type of the actual distribution command value.
   */
  record Redistribution<V extends UnifiedRecordValue>(
      long distributionKey,
      ValueType distributionValueType,
      Intent distributionIntent,
      V distributionValue,
      Set<RedistributableResource> containedResources) {}
}
