/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverDecision.CommittedFailover;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * The value of {@link SystemPartitionKeyType#REGION_FAILOVER}: the command to fail over from a
 * failed region to a surviving one, which initiates the failover procedure. It records the evidence
 * the decision was made on: which brokers of the failed region were out of sight, for how long, how
 * many observers confirmed it, and when the region was first detected as gone, as the leader waited
 * for the recovery window from then on.
 */
public final class RegionFailoverValue extends UnpackedObject {
  private final StringProperty failedRegionProp = new StringProperty("failedRegion", "");
  private final StringProperty targetRegionProp = new StringProperty("targetRegion", "");
  private final StringProperty leaderIdProp = new StringProperty("leaderId", "");
  private final LongProperty termProp = new LongProperty("term", 0L);
  private final LongProperty detectedAtMillisProp = new LongProperty("detectedAtMillis", 0L);
  private final LongProperty decidedAtMillisProp = new LongProperty("decidedAtMillis", 0L);
  private final ArrayProperty<GoneBrokerValue> goneBrokersProp =
      new ArrayProperty<>("goneBrokers", GoneBrokerValue::new);

  public RegionFailoverValue() {
    super(7);
    declareProperty(failedRegionProp)
        .declareProperty(targetRegionProp)
        .declareProperty(leaderIdProp)
        .declareProperty(termProp)
        .declareProperty(detectedAtMillisProp)
        .declareProperty(decidedAtMillisProp)
        .declareProperty(goneBrokersProp);
  }

  static RegionFailoverValue of(final RegionFailoverDecision decision) {
    final var value =
        new RegionFailoverValue()
            .setFailedRegion(decision.failedRegion())
            .setTargetRegion(decision.targetRegion())
            .setLeaderId(decision.leaderId())
            .setTerm(decision.term())
            .setDetectedAtMillis(decision.detectedAtMillis())
            .setDecidedAtMillis(decision.decidedAtMillis());
    decision
        .goneBrokers()
        .forEach(
            gone ->
                value
                    .goneBrokersProp
                    .add()
                    .setMemberId(gone.memberId())
                    .setGoneForMillis(gone.goneForMillis())
                    .setConfirmations(gone.confirmations()));
    return value;
  }

  CommittedFailover committed() {
    return new CommittedFailover(getFailedRegion(), getTargetRegion(), getDecidedAtMillis());
  }

  public String getFailedRegion() {
    return BufferUtil.bufferAsString(failedRegionProp.getValue());
  }

  public RegionFailoverValue setFailedRegion(final String failedRegion) {
    failedRegionProp.setValue(failedRegion);
    return this;
  }

  public String getTargetRegion() {
    return BufferUtil.bufferAsString(targetRegionProp.getValue());
  }

  public RegionFailoverValue setTargetRegion(final String targetRegion) {
    targetRegionProp.setValue(targetRegion);
    return this;
  }

  /** Returns the system partition leader that decided the failover. */
  public String getLeaderId() {
    return BufferUtil.bufferAsString(leaderIdProp.getValue());
  }

  public RegionFailoverValue setLeaderId(final String leaderId) {
    leaderIdProp.setValue(leaderId);
    return this;
  }

  /** Returns the term of the leader that decided the failover. */
  public long getTerm() {
    return termProp.getValue();
  }

  public RegionFailoverValue setTerm(final long term) {
    termProp.setValue(term);
    return this;
  }

  /**
   * Returns when the leader first detected the region as gone, by its wall clock. It waited for the
   * recovery window from then on, so the failover was decided at least that long after.
   */
  public long getDetectedAtMillis() {
    return detectedAtMillisProp.getValue();
  }

  public RegionFailoverValue setDetectedAtMillis(final long detectedAtMillis) {
    detectedAtMillisProp.setValue(detectedAtMillis);
    return this;
  }

  /** Returns when the leader decided the failover, by its wall clock. */
  public long getDecidedAtMillis() {
    return decidedAtMillisProp.getValue();
  }

  public RegionFailoverValue setDecidedAtMillis(final long decidedAtMillis) {
    decidedAtMillisProp.setValue(decidedAtMillis);
    return this;
  }

  /** Returns the brokers of the failed region that were out of sight, ordered by member id. */
  public List<GoneBroker> getGoneBrokers() {
    final var goneBrokers = new ArrayList<GoneBroker>();
    goneBrokersProp.forEach(
        gone ->
            goneBrokers.add(
                new GoneBroker(
                    gone.getMemberId(), gone.getGoneForMillis(), gone.getConfirmations())));
    return goneBrokers;
  }

  /** A broker out of sight when the failover was decided. */
  static final class GoneBrokerValue extends ObjectValue {
    private final StringProperty memberIdProp = new StringProperty("memberId", "");
    private final LongProperty goneForMillisProp = new LongProperty("goneForMillis", 0L);
    private final LongProperty confirmationsProp = new LongProperty("confirmations", 0L);

    GoneBrokerValue() {
      super(3);
      declareProperty(memberIdProp)
          .declareProperty(goneForMillisProp)
          .declareProperty(confirmationsProp);
    }

    String getMemberId() {
      return BufferUtil.bufferAsString(memberIdProp.getValue());
    }

    GoneBrokerValue setMemberId(final String memberId) {
      memberIdProp.setValue(memberId);
      return this;
    }

    long getGoneForMillis() {
      return goneForMillisProp.getValue();
    }

    GoneBrokerValue setGoneForMillis(final long goneForMillis) {
      goneForMillisProp.setValue(goneForMillis);
      return this;
    }

    int getConfirmations() {
      return (int) confirmationsProp.getValue();
    }

    GoneBrokerValue setConfirmations(final int confirmations) {
      confirmationsProp.setValue(confirmations);
      return this;
    }
  }
}
