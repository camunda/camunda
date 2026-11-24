/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Shared building blocks for mutual TLS: verifying the certificate a peer presents against a trust
 * bundle and an allow-list of identities. Used by the gRPC gateway, the REST gateway and the
 * cluster messaging service, each with its own configuration.
 */
@NullMarked
package io.camunda.zeebe.util.tls;

import org.jspecify.annotations.NullMarked;
