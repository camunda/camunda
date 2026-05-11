/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Redacts resolved secret values before they reach the durable logstream or any exporter. Used by
 * the job-activation and FEEL-evaluation processors to write a masked copy of the event record
 * alongside the real-valued response, so the worker (or HTTP caller) sees the underlying secret
 * while the on-disk record carries only {@link #MASKED_VALUE}.
 */
public final class SecretMasker {

  /** The string written in place of any masked secret value. */
  public static final String MASKED_VALUE = "***";

  private SecretMasker() {}

  /**
   * Returns a fresh MessagePack-encoded variables document in which every top-level entry whose key
   * starts with {@link SecretStore#FEEL_NAMESPACE} has had its value replaced by {@link
   * #MASKED_VALUE}. Buffers with no such entries are returned unchanged. The returned buffer is
   * always a fresh allocation when masking occurred — safe to retain across processor calls.
   */
  public static DirectBuffer maskSecretsInDocument(final DirectBuffer document) {
    if (document == null || document.capacity() == 0) {
      return document;
    }
    final Map<String, Object> map = MsgPackConverter.convertToMap(document);
    boolean changed = false;
    for (final var entry : map.entrySet()) {
      if (entry.getKey().startsWith(SecretStore.FEEL_NAMESPACE)) {
        entry.setValue(MASKED_VALUE);
        changed = true;
      }
    }
    if (!changed) {
      return document;
    }
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(map));
  }

  /**
   * Returns true if {@code expression} could resolve to a secret value at the standalone FEEL
   * evaluation endpoint, meaning the persisted {@code resultValue} should be masked before it
   * reaches the logstream. Uses a substring check on the namespace prefix rather than a real FEEL
   * parse — cheaper, and any false positive simply masks a value that would have been a harmless
   * reference string anyway.
   */
  public static boolean expressionReferencesSecret(final String expression) {
    return expression != null && expression.contains(SecretStore.FEEL_NAMESPACE);
  }

  /**
   * Returns a freshly-encoded MessagePack string buffer holding {@link #MASKED_VALUE}. Used by the
   * FEEL evaluation processor to stamp over a {@code resultValue} that originated from a
   * secret-resolving evaluation before the persistence path serializes it.
   */
  public static DirectBuffer maskedAsMsgPackString() {
    final MsgPackWriter writer = new MsgPackWriter();
    final MutableDirectBuffer writeBuffer = new ExpandableArrayBuffer();
    writer.wrap(writeBuffer, 0);

    final DirectBuffer stringWrapper = new UnsafeBuffer();
    stringWrapper.wrap(MASKED_VALUE.getBytes());
    writer.writeString(stringWrapper);

    final DirectBuffer resultView = new UnsafeBuffer();
    resultView.wrap(writeBuffer, 0, writer.getOffset());
    return resultView;
  }
}
