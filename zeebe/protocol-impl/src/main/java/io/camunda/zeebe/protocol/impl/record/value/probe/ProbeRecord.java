/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.probe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProbeRecord extends MsgpackBufferValue<ProbeRecord>
    implements MsgpackRecordValue, ProbeRecordValue {

  DirectBuffer probeId = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  DirectBuffer inputExpression = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer input = DocumentValue.EMPTY_DOCUMENT;

  final List<JobSecretReference> secretReferences = new ArrayList<>();
  long requestTimeout = 0L;
  long deadline = -1L;
  DirectBuffer worker = Decoding.EMPTY;
  ProbeOutcome outcome = ProbeOutcome.UNSPECIFIED;

  @MsgpackProperty(document = true)
  DirectBuffer data = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer message = Decoding.EMPTY;
  ProbeEndReason endReason = ProbeEndReason.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getProbeId() {
    return BufferUtil.bufferAsString(probeId);
  }

  public ProbeRecord setProbeId(final String probeId) {
    this.probeId = BufferUtil.wrapString(probeId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getProbeIdBuffer() {
    return probeId;
  }

  @Override
  public String getInputExpression() {
    return BufferUtil.bufferAsString(inputExpression);
  }

  public ProbeRecord setInputExpression(final String inputExpression) {
    this.inputExpression = BufferUtil.wrapString(inputExpression);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getInputExpressionBuffer() {
    return inputExpression;
  }

  @Override
  public Map<String, Object> getInput() {
    return MsgPackConverter.convertToMap(input);
  }

  public ProbeRecord setInput(final DirectBuffer input) {
    this.input = Decoding.document(input);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getInputBuffer() {
    return input;
  }

  @Override
  public List<JobSecretReferenceValue> getSecretReferences() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return secretReferences.stream()
        .map(
            element -> {
              final var copy = new JobSecretReference();
              copy.copy(element);
              return (JobSecretReferenceValue) copy;
            })
        .toList();
  }

  /**
   * Direct access to the secret reference elements, without the detached copies of {@link
   * #getSecretReferences()}. The elements stay owned by this record; do not hold on to them.
   */
  @JsonIgnore
  public ValueArray<JobSecretReference> secretReferences() {
    return new SecretReferences(secretReferences);
  }

  @JsonIgnore
  public boolean hasSecretReferences() {
    return !secretReferences.isEmpty();
  }

  public ProbeRecord addSecretReference(
      final String storeId, final String secretReference, final String path) {
    final var reference = new JobSecretReference();
    reference.setStoreId(storeId).setSecretReference(secretReference).setPath(path);
    secretReferences.add(reference);
    return this;
  }

  @Override
  public long getRequestTimeout() {
    return requestTimeout;
  }

  public ProbeRecord setRequestTimeout(final long requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  public ProbeRecord setDeadline(final long deadline) {
    this.deadline = deadline;
    return this;
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(worker);
  }

  public ProbeRecord setWorker(final String worker) {
    this.worker = BufferUtil.wrapString(worker);
    return this;
  }

  public ProbeRecord setWorker(final DirectBuffer worker) {
    this.worker = worker;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return worker;
  }

  @Override
  public ProbeOutcome getOutcome() {
    return outcome;
  }

  public ProbeRecord setOutcome(final ProbeOutcome outcome) {
    this.outcome = outcome;
    return this;
  }

  @Override
  public Map<String, Object> getData() {
    return MsgPackConverter.convertToMap(data);
  }

  public ProbeRecord setData(final DirectBuffer data) {
    this.data = Decoding.document(data);
    return this;
  }

  /**
   * Empties the data. The answer's data belongs on the {@code ANSWER} command only: an event that
   * is built from that command must reset it, so the data never reaches an exported event.
   */
  public ProbeRecord resetData() {
    data = DocumentValue.EMPTY_DOCUMENT;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDataBuffer() {
    return data;
  }

  @Override
  public String getMessage() {
    return BufferUtil.bufferAsString(message);
  }

  public ProbeRecord setMessage(final String message) {
    this.message = BufferUtil.wrapString(message);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMessageBuffer() {
    return message;
  }

  @Override
  public ProbeEndReason getEndReason() {
    return endReason;
  }

  public ProbeRecord setEndReason(final ProbeEndReason endReason) {
    this.endReason = endReason;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ProbeRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantId;
  }

  @Override
  protected MsgpackCodec<ProbeRecord> codec() {
    return ProbeRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProbeRecord> ops() {
    return ProbeRecordOps.INSTANCE;
  }

  private static final class SecretReferences implements ValueArray<JobSecretReference> {

    final List<JobSecretReference> references;

    private SecretReferences(final List<JobSecretReference> references) {
      this.references = references;
    }

    @Override
    public JobSecretReference add() {
      final var reference = new JobSecretReference();
      references.add(reference);
      return reference;
    }

    @Override
    public JobSecretReference add(final int index) {
      final var reference = new JobSecretReference();
      references.add(index, reference);
      return reference;
    }

    @Override
    public JobSecretReference get(final int index) {
      return references.get(index);
    }

    @Override
    public JobSecretReference remove(final int index) {
      return references.remove(index);
    }

    @Override
    public int size() {
      return references.size();
    }

    @Override
    public Stream<JobSecretReference> stream() {
      return references.stream();
    }

    @Override
    public Iterator<JobSecretReference> iterator() {
      return references.iterator();
    }
  }
}
