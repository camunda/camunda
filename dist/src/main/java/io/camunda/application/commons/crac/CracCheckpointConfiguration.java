/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import java.util.List;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Camunda {@link Resource} beans into the global CRaC context so they can release (and later
 * re-acquire) open file descriptors — Elasticsearch/OpenSearch sockets, the Zeebe gRPC channel, and
 * netty event loops — around a CRaC checkpoint/restore.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}, and only effective on a CRaC-enabled JVM (see
 * {@code camunda.Dockerfile} {@code --build-arg BASE=crac}). On a standard JVM the {@code org.crac}
 * facade is a no-op, so this has zero effect on normal startup.
 *
 * <p>This is the enabling foundation: it provides the registration point. Each component still
 * needs to contribute a {@link Resource} bean that closes its clients in {@link
 * Resource#beforeCheckpoint(Context)} and reopens them in {@link Resource#afterRestore(Context)}.
 * See {@code package-info.java} for the known integration points discovered by the spike.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class CracCheckpointConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(CracCheckpointConfiguration.class);

  // Strong references: the global CRaC context holds resources weakly, so registered
  // participants must be kept alive for the lifetime of the application context.
  private final List<Resource> participants;

  public CracCheckpointConfiguration(final ObjectProvider<Resource> resourceProvider) {
    participants = resourceProvider.orderedStream().toList();
    final Context<Resource> globalContext = Core.getGlobalContext();
    participants.forEach(globalContext::register);
    LOG.info("Registered {} CRaC checkpoint participant(s)", participants.size());
  }
}
