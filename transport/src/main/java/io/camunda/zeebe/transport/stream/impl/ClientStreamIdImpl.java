/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import java.util.UUID;

/**
 * ID of a ClientStream based on the id of the aggregated stream and a local id
 *
 * @param serverStreamId id of the aggregated server stream in which the client stream is part of
 * @param localId id wich can uniquely identify this client among all clients in its aggregated
 *     server stream.
 */
record ClientStreamIdImpl(UUID serverStreamId, int localId) implements ClientStreamId {}
