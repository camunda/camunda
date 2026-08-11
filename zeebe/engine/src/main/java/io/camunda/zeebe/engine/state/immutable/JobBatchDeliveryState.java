/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.jobbatch.PendingJobBatchDelivery;
import java.util.Optional;
import java.util.function.BiPredicate;

public interface JobBatchDeliveryState {

  Optional<PendingJobBatchDelivery> getPendingDelivery(long deliveryAttemptKey);

  /**
   * Visits pending deliveries whose deadline is strictly before {@code executionTimestamp}.
   *
   * @return the last visited index when the callback stops iteration early, otherwise {@code null}
   */
  DeliveryDeadlineIndex forEachTimedOutDelivery(
      long executionTimestamp,
      DeliveryDeadlineIndex startAt,
      BiPredicate<Long, PendingJobBatchDelivery> callback);

  record DeliveryDeadlineIndex(long deadline, long deliveryAttemptKey) {}
}
