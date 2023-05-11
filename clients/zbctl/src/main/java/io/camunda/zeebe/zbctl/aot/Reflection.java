/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.aot;

import io.camunda.zeebe.client.impl.response.BrokerInfoImpl;
import io.camunda.zeebe.client.impl.response.PartitionInfoImpl;
import io.camunda.zeebe.client.impl.response.TopologyImpl;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(
    targets = {TopologyImpl.class, BrokerInfoImpl.class, PartitionInfoImpl.class})
public class Reflection {}
