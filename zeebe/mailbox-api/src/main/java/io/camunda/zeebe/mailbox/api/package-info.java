/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Interfaces and value types of the cross-region mailbox.
 *
 * <p>The mailbox carries messages between nodes that cannot talk to each other over the network,
 * because they run in different regions. It is a proposal that is still being evaluated. The only
 * link between the regions is the shared database, so the mailbox is stored there.
 *
 * <p>This module contains no storage implementation. A {@link
 * io.camunda.zeebe.mailbox.api.MailboxStore} implementation is provided separately.
 */
@NullMarked
package io.camunda.zeebe.mailbox.api;

import org.jspecify.annotations.NullMarked;
