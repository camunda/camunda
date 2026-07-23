/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.objects;

/**
 * One way a {@link CompiledObjectType} recognizes that its own instances have reached the end of
 * their lifecycle — see {@link ObjectTypes#onProcessCompletion(String)} for the one concrete rule
 * v1 ships, and {@code io.camunda.analytics.lake.translate.LakeTranslator}'s own "Object lifecycle
 * capture" javadoc section for how a declared rule is matched against a completing instance.
 *
 * <p>Sealed with a single implementation deliberately: v1 vocabulary is process-completion only.
 * State-, element-, and message-triggered closing are phased later — this interface exists now so
 * that extension is additive (a new record implementation plus a new {@code switch} arm at each
 * call site), never a breaking change to {@link ObjectTypes.Builder#closes(ClosingRule)}'s own
 * signature or to any already-compiled declaration.
 */
public sealed interface ClosingRule {

  /**
   * Closes every open instance of the declaring object type the moment a process instance of {@code
   * bpmnProcessId} completes (successfully or by termination — see {@code LakeTranslator}'s own
   * outcome mapping) <em>and</em> that completing instance itself sighted the object (see {@code
   * LakeTranslator}'s "Object fabric capture" section for what a sighting is). An object type may
   * declare more than one such rule (one per closing process).
   */
  record OnProcessCompletion(String bpmnProcessId) implements ClosingRule {}
}
