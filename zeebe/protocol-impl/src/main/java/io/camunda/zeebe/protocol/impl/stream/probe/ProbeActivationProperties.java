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
 * The metadata of a probe stream, i.e. of one subscription of a probe worker for one capability.
 * Mirrors {@link io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties} without what
 * only jobs need (timeout, fetched variables, lease).
 */
public interface ProbeActivationProperties extends BufferWriter {

  /** Returns the name of the worker, recorded on the probe when it is pushed. */
  DirectBuffer worker();

  /** Returns the tenants the worker answers probes for, unless the filter is ASSIGNED. */
  Collection<String> tenantIds();

  /** Returns how the tenants of the worker are determined. */
  TenantFilter tenantFilter();

  /** Returns the claims of the principal that opened the stream. */
  Map<String, Object> claims();
}
