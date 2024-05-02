/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.records;

import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import java.util.function.BiPredicate;

/**
 * Takes as argument the potential next batch entry count and the next potential batch size, in
 * order to verify whether this next {@link RecordBatchEntry} can be added to the {@link
 * RecordBatch}.
 */
public interface RecordBatchSizePredicate extends BiPredicate<Integer, Integer> {}
