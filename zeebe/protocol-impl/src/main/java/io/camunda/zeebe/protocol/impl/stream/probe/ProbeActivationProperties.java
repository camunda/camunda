/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.probe;

import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.Map;
import org.agrona.DirectBuffer;

/**
 * The metadata of a probe stream, i.e. of one probe worker and every probe it answers. Mirrors
 * {@link io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties} without what only jobs
 * need (timeout, fetched variables, lease).
 */
public interface ProbeActivationProperties extends BufferWriter {

  /**
   * The type of every probe stream. Probes are rare, so a worker holds one stream for all the
   * probes it answers instead of one per probe, and the engine matches a probe against {@link
   * #probeIds()}.
   */
  String STREAM_TYPE = "probes";

  /** Returns the IDs of the probes the worker answers, each matched exactly. */
  Collection<String> probeIds();

  /** Returns the name of the worker, recorded on the probe when it is pushed. */
  DirectBuffer worker();

  /** Returns the tenants the worker answers probes for, unless the filter is ASSIGNED. */
  Collection<String> tenantIds();

  /** Returns how the tenants of the worker are determined. */
  TenantFilter tenantFilter();

  /** Returns the claims of the principal that opened the stream. */
  Map<String, Object> claims();
}
