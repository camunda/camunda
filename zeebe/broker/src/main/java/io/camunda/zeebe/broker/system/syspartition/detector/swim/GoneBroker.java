/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

/**
 * A broker that was out of sight when a region failover was decided.
 *
 * @param memberId the broker
 * @param goneForMillis for how long no observer saw it
 * @param confirmations how many observers confirmed that they did not
 */
public record GoneBroker(String memberId, long goneForMillis, int confirmations) {}
