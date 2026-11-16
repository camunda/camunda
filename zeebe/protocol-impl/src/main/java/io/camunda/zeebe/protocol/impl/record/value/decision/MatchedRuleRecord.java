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
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
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
public final class MatchedRuleRecord extends MsgpackBufferValue<MatchedRuleRecord>
    implements MsgpackRecordValue, MatchedRuleValue {

  @Nullable DirectBuffer ruleId;
  final RequiredInt ruleIndex = new RequiredInt();
  final List<EvaluatedOutputRecord> evaluatedOutputs = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getRuleId() {
    return bufferAsString(Decoding.required(ruleId, "ruleId"));
  }

  public MatchedRuleRecord setRuleId(final String ruleId) {
    this.ruleId = BufferUtil.wrapString(ruleId);
    return this;
  }

  @Override
  public int getRuleIndex() {
    return ruleIndex.get();
  }

  public MatchedRuleRecord setRuleIndex(final int ruleIndex) {
    this.ruleIndex.set(ruleIndex);
    return this;
  }

  @Override
  public List<EvaluatedOutputValue> getEvaluatedOutputs() {
    final List<EvaluatedOutputValue> copies = new ArrayList<>();
    for (final EvaluatedOutputRecord evaluatedOutput : evaluatedOutputs) {
      final var copyRecord = new EvaluatedOutputRecord();
      final var copyBuffer = BufferUtil.createCopy(evaluatedOutput);
      copyRecord.wrap(copyBuffer);
      copies.add(copyRecord);
    }
    return copies;
  }

  @JsonIgnore
  public DirectBuffer getRuleIdBuffer() {
    return Decoding.required(ruleId, "ruleId");
  }

  @JsonIgnore
  public ValueArray<EvaluatedOutputRecord> evaluatedOutputs() {
    return new EvaluatedOutputs(evaluatedOutputs);
  }

  @Override
  protected MsgpackCodec<MatchedRuleRecord> codec() {
    return MatchedRuleRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MatchedRuleRecord> ops() {
    return MatchedRuleRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of {@code evaluatedOutputs}: it adds and removes the elements of the list,
   * so that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class EvaluatedOutputs implements ValueArray<EvaluatedOutputRecord> {

    final List<EvaluatedOutputRecord> outputs;

    private EvaluatedOutputs(final List<EvaluatedOutputRecord> outputs) {
      this.outputs = outputs;
    }

    @Override
    public EvaluatedOutputRecord add() {
      final var output = new EvaluatedOutputRecord();
      outputs.add(output);
      return output;
    }

    @Override
    public EvaluatedOutputRecord add(final int index) {
      final var output = new EvaluatedOutputRecord();
      outputs.add(index, output);
      return output;
    }

    @Override
    public EvaluatedOutputRecord get(final int index) {
      return outputs.get(index);
    }

    @Override
    public EvaluatedOutputRecord remove(final int index) {
      return outputs.remove(index);
    }

    @Override
    public int size() {
      return outputs.size();
    }

    @Override
    public Stream<EvaluatedOutputRecord> stream() {
      return outputs.stream();
    }

    @Override
    public Iterator<EvaluatedOutputRecord> iterator() {
      return outputs.iterator();
    }
  }
}
