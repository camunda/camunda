/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;

public interface ManagedScriptDefinitionState {

  Long getManagedScriptDefinitionKey(long processDefinitionKey, DirectBuffer elementId);

  ManagedScriptDefinitionRecord getManagedScriptDefinition(long managedScriptDefinitionKey);

  void forEachManagedScriptDefinitionKey(LongConsumer callback);

  void forEachManagedScriptDefinitionKey(long processDefinitionKey, LongConsumer callback);
}
