/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * The mailbox and the partition leases in a relational database, through MyBatis. {@link
 * io.camunda.zeebe.mailbox.rdbms.RdbmsMailboxStores} builds both stores on a connection pool whose
 * schema was migrated with {@link io.camunda.zeebe.mailbox.rdbms.RdbmsMailboxSchema#CHANGELOG}.
 */
@NullMarked
package io.camunda.zeebe.mailbox.rdbms;

import org.jspecify.annotations.NullMarked;
