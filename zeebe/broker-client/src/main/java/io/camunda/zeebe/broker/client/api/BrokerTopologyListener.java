/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.atomix.cluster.MemberId;

/** Listener which will be notified when a broker is added or removed from the topology. */
public interface BrokerTopologyListener {

  default void brokerAdded(final MemberId memberId) {}

  default void brokerRemoved(final MemberId memberId) {}

  default void completedClusterChange() {}
}
