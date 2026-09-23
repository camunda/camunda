/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/*
If we had a partial bulk update failure then we want to still send (try) to send notifications
for the incidents that were updated.  Otherwise, as they have already been updated we will
not send notifications if we retry the batch
*/
public final class IncidentUpdateIdsResponse {
  private final List<String> updatedIds;
  private final RuntimeException error;

  IncidentUpdateIdsResponse(final List<String> updatedIds, final RuntimeException error) {
    this.updatedIds = updatedIds;
    this.error = error;
  }

  @VisibleForTesting
  IncidentUpdateIdsResponse withError(final RuntimeException error) {
    return new IncidentUpdateIdsResponse(updatedIds, error);
  }

  public CompletableFuture<Integer> processUpdatedCount() {
    return processUpdatedIds(updatedIds -> CompletableFuture.completedFuture(null));
  }

  public CompletableFuture<Integer> processUpdatedIds(
      final Function<List<String>, CompletableFuture<Void>> asyncCallback) {
    return asyncCallback
        // run callback first
        .apply(updatedIds)
        .thenApply(
            ignored -> {
              // then raise error if there was one, so that we can still process the updated ids
              if (error != null) {
                throw error;
              }
              return updatedIds.size();
            });
  }

  @Override
  public int hashCode() {
    return Objects.hash(updatedIds, error);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentUpdateIdsResponse that = (IncidentUpdateIdsResponse) o;
    return Objects.equals(updatedIds, that.updatedIds) && Objects.equals(error, that.error);
  }
}
