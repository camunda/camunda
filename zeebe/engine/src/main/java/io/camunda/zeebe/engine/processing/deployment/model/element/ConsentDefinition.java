/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.protocol.record.value.ConsentGround;

/**
 * What a gated task declares: the ground it rests on, the connection and operation it acts on as
 * fixed values, and the expressions that name the subject, the subject's target id and the change.
 *
 * @param subject the subject's username, or {@code null} for the requester (consent ground only)
 * @param target the subject's id in the target system (relationship ground only)
 * @param change the value an approval shows and binds, or {@code null}
 */
public record ConsentDefinition(
    ConsentGround ground,
    String connection,
    String operation,
    Expression subject,
    Expression target,
    Expression change) {}
