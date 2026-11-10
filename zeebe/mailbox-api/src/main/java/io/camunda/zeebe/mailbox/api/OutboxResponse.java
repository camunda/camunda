/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * The response to a {@link MessageKind#CLIENT_REQUEST}, addressed to the gateway that sent it.
 *
 * <p>The payload is treated as immutable. Callers must not change the array.
 *
 * @param gatewayId the gateway that must receive the response
 * @param requestId the request id that the gateway used in the inbox message
 * @param payload the serialized response
 */
public record OutboxResponse(GatewayId gatewayId, long requestId, byte[] payload) {}
