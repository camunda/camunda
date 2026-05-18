/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client;

import io.atomix.cluster.BrokerMemberId;

public class BrokerMemberIds {
  public static final BrokerMemberId ZERO = BrokerMemberId.from(0);
  public static final BrokerMemberId ONE = BrokerMemberId.from(1);
  public static final BrokerMemberId TWO = BrokerMemberId.from(2);
}
