/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;

/**
 * There is a good chance that we will get rid of this context, and use a "ProcessingContext"
 * defined by the StreamProcessor. *
 */
public record Context(
    ZeebeDb zeebeDb, TransactionContext transactionContext, ProcessingScheduleService executor) {}
