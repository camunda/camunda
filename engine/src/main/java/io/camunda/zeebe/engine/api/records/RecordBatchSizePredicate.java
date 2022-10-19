/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api.records;

import io.camunda.zeebe.logstreams.RecordBatchEntry;
import java.util.function.BiPredicate;

/**
 * Takes as argument the potential next batch entry count and the next potential batch size, in
 * order to verify whether this next {@link RecordBatchEntry} can be added to the {@link
 * RecordBatch}.
 */
public interface RecordBatchSizePredicate extends BiPredicate<Integer, Integer> {}
