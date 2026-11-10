/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * The contract for partition leases in the database, which decide who leads a partition across
 * regions. The database implementation is plugged in from outside.
 */
@NullMarked
package io.camunda.zeebe.mailbox.api.leadership;

import org.jspecify.annotations.NullMarked;
