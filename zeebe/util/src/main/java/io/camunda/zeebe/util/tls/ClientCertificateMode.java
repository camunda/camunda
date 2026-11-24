/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

/** Whether, and how strictly, a TLS server asks its peers for a certificate. */
public enum ClientCertificateMode {
  /** No certificate is requested; today's behavior. */
  NONE,
  /**
   * A certificate is requested but not required. A presented certificate is verified, and a failure
   * is logged and counted, but the connection is still accepted. Meant to observe which clients
   * would be rejected before switching to {@link #REQUIRED}, or to roll out mTLS across a running
   * cluster.
   */
  OPTIONAL,
  /** A valid, allowed certificate is required; anything else fails the handshake. */
  REQUIRED
}
