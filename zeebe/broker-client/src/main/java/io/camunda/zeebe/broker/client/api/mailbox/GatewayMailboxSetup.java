/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api.mailbox;

import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import java.util.concurrent.Executor;

/**
 * What the gateway needs to send requests through the mailbox.
 *
 * @param store the storage of the mailbox
 * @param config the settings of the mailbox
 * @param gatewayId identifies this gateway process in the outbox. Unique among all gateways of the
 *     cluster, and the same for the whole life of the process.
 * @param executor runs the calls to the store, which may block. Must not be an actor thread.
 */
public record GatewayMailboxSetup(
    MailboxStore store, MailboxConfig config, String gatewayId, Executor executor) {}
