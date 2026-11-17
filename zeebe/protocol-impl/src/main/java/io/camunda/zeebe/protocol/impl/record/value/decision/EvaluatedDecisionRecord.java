/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.decision;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class EvaluatedDecisionRecord extends MsgpackBufferValue<EvaluatedDecisionRecord>
    implements MsgpackRecordValue, EvaluatedDecisionValue {

  @Nullable DirectBuffer decisionId;
  DirectBuffer decisionEvaluationInstanceKey = Decoding.EMPTY;
  @Nullable DirectBuffer decisionName;
  final RequiredLong decisionKey = new RequiredLong();
  final RequiredInt decisionVersion = new RequiredInt();
  @Nullable DirectBuffer decisionType;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer decisionOutput;

  final List<EvaluatedInputRecord> evaluatedInputs = new ArrayList<>();
  final List<MatchedRuleRecord> matchedRules = new ArrayList<>();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getDecisionId() {
    return bufferAsString(Decoding.required(decisionId, "decisionId"));
  }

  public EvaluatedDecisionRecord setDecisionId(final String decisionId) {
    this.decisionId = BufferUtil.wrapString(decisionId);
    return this;
  }

  @Override
  @JsonIgnore
  public String getDecisionEvaluationInstanceKey() {
    return bufferAsString(decisionEvaluationInstanceKey);
  }

  public EvaluatedDecisionRecord setDecisionEvaluationInstanceKey(
      final String decisionEvaluationInstanceKey) {
    this.decisionEvaluationInstanceKey = BufferUtil.wrapString(decisionEvaluationInstanceKey);
    return this;
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(Decoding.required(decisionName, "decisionName"));
  }

  public EvaluatedDecisionRecord setDecisionName(final String decisionName) {
    this.decisionName = BufferUtil.wrapString(decisionName);
    return this;
  }

  @Override
  public long getDecisionKey() {
    return decisionKey.get();
  }

  public EvaluatedDecisionRecord setDecisionKey(final long decisionKey) {
    this.decisionKey.set(decisionKey);
    return this;
  }

  @Override
  public int getDecisionVersion() {
    return decisionVersion.get();
  }

  public EvaluatedDecisionRecord setDecisionVersion(final int decisionVersion) {
    this.decisionVersion.set(decisionVersion);
    return this;
  }

  @Override
  public String getDecisionType() {
    return bufferAsString(Decoding.required(decisionType, "decisionType"));
  }

  public EvaluatedDecisionRecord setDecisionType(final String decisionType) {
    this.decisionType = BufferUtil.wrapString(decisionType);
    return this;
  }

  @Override
  public String getDecisionOutput() {
    return MsgPackConverter.convertToJson(Decoding.required(decisionOutput, "decisionOutput"));
  }

  public EvaluatedDecisionRecord setDecisionOutput(final DirectBuffer decisionOutput) {
    this.decisionOutput = decisionOutput;
    return this;
  }

  @Override
  public List<EvaluatedInputValue> getEvaluatedInputs() {
    final List<EvaluatedInputValue> copies = new ArrayList<>();
    for (final EvaluatedInputRecord evaluatedInput : evaluatedInputs) {
      final var copyRecord = new EvaluatedInputRecord();
      final var copyBuffer = BufferUtil.createCopy(evaluatedInput);
      copyRecord.wrap(copyBuffer);
      copies.add(copyRecord);
    }
    return copies;
  }

  @Override
  public List<MatchedRuleValue> getMatchedRules() {
    final List<MatchedRuleValue> copies = new ArrayList<>();
    for (final MatchedRuleRecord matchedRule : matchedRules) {
      final var copyRecord = new MatchedRuleRecord();
      final var copyBuffer = BufferUtil.createCopy(matchedRule);
      copyRecord.wrap(copyBuffer);
      copies.add(copyRecord);
    }
    return copies;
  }

  @JsonIgnore
  public DirectBuffer getDecisionOutputBuffer() {
    return Decoding.required(decisionOutput, "decisionOutput");
  }

  @JsonIgnore
  public ValueArray<MatchedRuleRecord> matchedRules() {
    return new MatchedRules(matchedRules);
  }

  @JsonIgnore
  public ValueArray<EvaluatedInputRecord> evaluatedInputs() {
    return new EvaluatedInputs(evaluatedInputs);
  }

  @JsonIgnore
  public DirectBuffer getDecisionIdBuffer() {
    return Decoding.required(decisionId, "decisionId");
  }

  @JsonIgnore
  public DirectBuffer getDecisionNameBuffer() {
    return Decoding.required(decisionName, "decisionName");
  }

  @JsonIgnore
  public DirectBuffer getDecisionTypeBuffer() {
    return Decoding.required(decisionType, "decisionType");
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public EvaluatedDecisionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<EvaluatedDecisionRecord> codec() {
    return EvaluatedDecisionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<EvaluatedDecisionRecord> ops() {
    return EvaluatedDecisionRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of {@code matchedRules}: it adds and removes the elements of the list, so
   * that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class MatchedRules implements ValueArray<MatchedRuleRecord> {

    final List<MatchedRuleRecord> rules;

    private MatchedRules(final List<MatchedRuleRecord> rules) {
      this.rules = rules;
    }

    @Override
    public MatchedRuleRecord add() {
      final var rule = new MatchedRuleRecord();
      rules.add(rule);
      return rule;
    }

    @Override
    public MatchedRuleRecord add(final int index) {
      final var rule = new MatchedRuleRecord();
      rules.add(index, rule);
      return rule;
    }

    @Override
    public MatchedRuleRecord get(final int index) {
      return rules.get(index);
    }

    @Override
    public MatchedRuleRecord remove(final int index) {
      return rules.remove(index);
    }

    @Override
    public int size() {
      return rules.size();
    }

    @Override
    public Stream<MatchedRuleRecord> stream() {
      return rules.stream();
    }

    @Override
    public Iterator<MatchedRuleRecord> iterator() {
      return rules.iterator();
    }
  }

  /**
   * The mutable accessor of {@code evaluatedInputs}: it adds and removes the elements of the list,
   * so that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class EvaluatedInputs implements ValueArray<EvaluatedInputRecord> {

    final List<EvaluatedInputRecord> inputs;

    private EvaluatedInputs(final List<EvaluatedInputRecord> inputs) {
      this.inputs = inputs;
    }

    @Override
    public EvaluatedInputRecord add() {
      final var input = new EvaluatedInputRecord();
      inputs.add(input);
      return input;
    }

    @Override
    public EvaluatedInputRecord add(final int index) {
      final var input = new EvaluatedInputRecord();
      inputs.add(index, input);
      return input;
    }

    @Override
    public EvaluatedInputRecord get(final int index) {
      return inputs.get(index);
    }

    @Override
    public EvaluatedInputRecord remove(final int index) {
      return inputs.remove(index);
    }

    @Override
    public int size() {
      return inputs.size();
    }

    @Override
    public Stream<EvaluatedInputRecord> stream() {
      return inputs.stream();
    }

    @Override
    public Iterator<EvaluatedInputRecord> iterator() {
      return inputs.iterator();
    }
  }
}
