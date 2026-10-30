/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobConsent;
import io.camunda.zeebe.protocol.record.value.ConsentGround;
import org.agrona.DirectBuffer;

/**
 * The consent declaration of a task, evaluated when its job is created: the expressions resolved in
 * the element's scope, and the requester read from the process instance.
 *
 * @param credential the name of the secret holding the connection's credential, or {@code null}
 */
public record EvaluatedConsent(
    ConsentGround ground,
    String connection,
    String operation,
    String subject,
    String requester,
    String target,
    DirectBuffer change,
    String credential) {

  public static final EvaluatedConsent NONE =
      new EvaluatedConsent(
          ConsentGround.NONE, "", "", "", "", "", DocumentValue.EMPTY_DOCUMENT, null);

  public void applyTo(final JobConsent consent) {
    consent
        .setGround(ground)
        .setConnection(connection)
        .setOperation(operation)
        .setSubject(subject)
        .setRequester(requester)
        .setTarget(target)
        .setChange(change);
  }
}
