/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Consolidates the SWIM views the brokers reported into one liveness per broker. SWIM views are
 * local, lagging, and sometimes wrong, so the rules are lopsided on purpose: failing over a region
 * that is alive is far worse than waiting a little longer for one that is gone.
 *
 * <ul>
 *   <li><b>Any sighting wins.</b> A broker is healthy while at least one fresh report lists it
 *       alive, or it reports itself. A broker out of sight of the leader but seen by another
 *       observer, as when the control region is cut off from one data region alone, is alive.
 *       Sightings are never outvoted by suspicions, because SWIM refutes its own suspicions.
 *   <li><b>Suspicion needs company.</b> A broker is dead only if at least {@code minObservers}
 *       distinct credible observers confirm it is out of sight, and every one of them has not seen
 *       it for the detection window. The shortest time counts, so one observer that saw it a moment
 *       ago postpones the verdict.
 *   <li><b>Silence is not evidence.</b> Observers whose reports are stale contribute nothing,
 *       neither sightings nor suspicions, and a broker nobody has evidence about is unknown rather
 *       than gone.
 *   <li><b>Isolated observers are ignored.</b> An observer that sees no other broker alive is not
 *       credible: its suspicions are not counted.
 * </ul>
 */
final class SwimViewConsolidator {
  private final Set<String> brokers;
  private final long detectionWindowMillis;
  private final int minObservers;

  /**
   * @param brokers every broker of the cluster
   * @param detectionWindowMillis how long all confirming observers must have not seen a broker, the
   *     detection window
   * @param minObservers how many credible observers must confirm a broker is out of sight
   */
  SwimViewConsolidator(
      final Set<String> brokers, final long detectionWindowMillis, final int minObservers) {
    this.brokers = brokers;
    this.detectionWindowMillis = detectionWindowMillis;
    this.minObservers = minObservers;
  }

  /** Returns the view of every broker given the fresh reports, ordered by member id. */
  Map<String, BrokerView> consolidate(final Collection<SwimReport> freshReports) {
    final var views = new TreeMap<String, BrokerView>();
    for (final var broker : brokers) {
      views.put(broker, consolidate(broker, freshReports));
    }
    return views;
  }

  private BrokerView consolidate(final String broker, final Collection<SwimReport> freshReports) {
    var visibleTo = 0;
    var confirmations = 0;
    var goneForMillis = Long.MAX_VALUE;
    for (final var report : freshReports) {
      if (report.observerId().equals(broker)) {
        // reporting is proof of life, and a report that arrived is proof of a path to this broker
        visibleTo++;
        continue;
      }
      final var observation = report.observationOf(broker);
      if (observation.state() == ObservedState.ALIVE) {
        visibleTo++;
      } else if (observation.state().isOutOfSight() && report.isCredible()) {
        confirmations++;
        goneForMillis = Math.min(goneForMillis, observation.unreachableMillis());
      }
    }

    if (visibleTo > 0) {
      return new BrokerView(broker, MemberLivenessState.HEALTHY, 0, 0, visibleTo);
    }
    if (confirmations == 0) {
      return BrokerView.unknown(broker);
    }
    final var dead = confirmations >= minObservers && goneForMillis >= detectionWindowMillis;
    return new BrokerView(
        broker,
        dead ? MemberLivenessState.DEAD : MemberLivenessState.SUSPECT,
        goneForMillis,
        confirmations,
        0);
  }
}
