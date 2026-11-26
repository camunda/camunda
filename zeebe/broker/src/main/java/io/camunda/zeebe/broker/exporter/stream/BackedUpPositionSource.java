/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

/**
 * Tells the {@link ExporterDirector} up to which log position of its partition the records are
 * contained in a backup that the whole cluster can be restored from. Used only when exporting is
 * restricted to backed-up records.
 */
@NullMarked
@FunctionalInterface
public interface BackedUpPositionSource {

  /**
   * Completes with the highest log position of this partition that a restore would contain, {@code
   * -1} when no common backup exists across all partitions, or empty when it cannot be determined
   * right now (for example while topology is not initialized or a newer candidate backup failed on
   * some partition while an earlier common backup may still exist). On empty, the director keeps
   * its previous position.
   */
  CompletionStage<OptionalLong> backedUpPosition();
}
