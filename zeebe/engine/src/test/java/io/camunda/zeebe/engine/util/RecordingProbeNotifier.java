/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.processing.streamprocessor.ProbeNotifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A {@link ProbeNotifier} for engine tests: records the probe IDs it was told about. */
public final class RecordingProbeNotifier implements ProbeNotifier {

  private final List<String> notifiedProbeIds = new CopyOnWriteArrayList<>();

  @Override
  public void notifyProbesAvailable(final String probeId) {
    notifiedProbeIds.add(probeId);
  }

  public List<String> getNotifiedProbeIds() {
    return notifiedProbeIds;
  }
}
