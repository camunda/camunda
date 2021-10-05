/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.protocol.jackson.record.VariableDocumentRecordValueBuilder.ImmutableVariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import org.immutables.value.Value;

@Value.Immutable
@ZeebeStyle
@JsonDeserialize(as = ImmutableVariableDocumentRecordValue.class)
public abstract class AbstractVariableDocumentRecordValue
    implements VariableDocumentRecordValue, DefaultJsonSerializable {}
