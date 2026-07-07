/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * INTERNAL: SBE-generated flyweights for the segment-shuffle wire format (schema id 300, generated
 * from {@code src/main/resources/sbe/segment-shuffle.xml}). The generator emits only public
 * classes, so this package cannot be package-private — treat it as private API regardless. Nothing
 * outside {@link io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelopeCodec} may reference it;
 * every other consumer (including the analytics engine) sees only the hand-written facade in {@code
 * io.camunda.eventbridge.streaming.shuffle} ({@code ShuffleEnvelope}, {@code CellDelta}, {@code
 * ShufflePayloadKind}, {@code ShuffleOperation}, {@code ShuffleEnvelopeCodec}).
 */
package io.camunda.eventbridge.streaming.shuffle.sbe;
