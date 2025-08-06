/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper;

public class NodeIdMapper {

  // Start before all services in broker

  // it should get a nodeId and pass it to all components that need it

  // Regular heartbeats to S3 to update the lease

  // When it cannot talk to S3 or the lease expires, it should stop all services

  // On shutdown it should the lease in S3
}
