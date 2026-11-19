/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.record.InternalOperationType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The internal operation a record belongs to: what kind of operation it is and its key. Keeping
 * both in one value means a processor cannot set a reference without saying what it points to.
 *
 * <p>It holds the stored type code rather than the {@link InternalOperationType}. A reference read
 * from a record written by a newer version keeps its code when it is passed on, even though this
 * version maps it to {@link InternalOperationType#UNKNOWN}. Only {@link #of(InternalOperationType,
 * long)} creates new references, and it never writes {@code UNKNOWN}.
 */
@NullMarked
public final class InternalOperationReference {

  public static final InternalOperationReference NONE =
      new InternalOperationReference(
          InternalOperationType.UNSPECIFIED.code(),
          RecordMetadataDecoder.internalOperationReferenceNullValue());

  private final int typeCode;
  private final long key;

  private InternalOperationReference(final int typeCode, final long key) {
    this.typeCode = typeCode;
    this.key = key;
  }

  /**
   * A reference to a new internal operation.
   *
   * @param type the kind of operation; never {@link InternalOperationType#UNKNOWN}, which only
   *     stands for codes written by a newer version
   * @param key the key of the operation
   */
  public static InternalOperationReference of(final InternalOperationType type, final long key) {
    if (type == InternalOperationType.UNKNOWN) {
      throw new IllegalArgumentException(
          "Expected a known internal operation type, but was UNKNOWN");
    }
    return ofStored(type.code(), key);
  }

  public static InternalOperationReference policyUpdate(final long updateKey) {
    return of(InternalOperationType.POLICY_UPDATE, updateKey);
  }

  /** The internal operation of the record, keeping a type code this version doesn't know. */
  public static InternalOperationReference of(final Record<?> record) {
    return ofStored(record.getInternalOperationType(), record.getInternalOperationReference());
  }

  /**
   * The internal operation as stored in the log, keeping a type code this version doesn't know.
   *
   * @param typeCode the stored code, see {@link InternalOperationType#code()}
   * @param key the key of the operation
   */
  public static InternalOperationReference ofStored(final int typeCode, final long key) {
    if (typeCode < RecordMetadataEncoder.internalOperationTypeMinValue()
        || typeCode > RecordMetadataEncoder.internalOperationTypeMaxValue()) {
      throw new IllegalArgumentException(
          "Expected internal operation type code to be between %d and %d, but was %d"
              .formatted(
                  RecordMetadataEncoder.internalOperationTypeMinValue(),
                  RecordMetadataEncoder.internalOperationTypeMaxValue(),
                  typeCode));
    }
    return NONE.typeCode == typeCode && NONE.key == key
        ? NONE
        : new InternalOperationReference(typeCode, key);
  }

  /**
   * @return the kind of operation, or {@link InternalOperationType#UNKNOWN} for a code written by a
   *     newer version
   */
  public InternalOperationType type() {
    return InternalOperationType.fromCode(typeCode);
  }

  /** The stored code of the type, including codes this version doesn't know. */
  public int typeCode() {
    return typeCode;
  }

  public long key() {
    return key;
  }

  /** Whether the record belongs to an internal operation. */
  public boolean isPresent() {
    return !equals(NONE);
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    return o instanceof final InternalOperationReference that
        && typeCode == that.typeCode
        && key == that.key;
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeCode, key);
  }

  @Override
  public String toString() {
    final var type = type();
    return (type == InternalOperationType.UNKNOWN ? "UNKNOWN(" + typeCode + ")" : type.name())
        + ":"
        + key;
  }
}
