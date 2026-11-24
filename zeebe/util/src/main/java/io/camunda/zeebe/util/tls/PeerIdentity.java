/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a peer's leaf certificate claims to be, for logging and matching.
 *
 * @param subject the full subject distinguished name
 * @param subjectAlternativeNames DNS, IP, URI and email SAN entries, in certificate order
 * @param commonName the subject's CN, if any
 * @param peerAddress the remote host and port, if known
 */
public record PeerIdentity(
    String subject,
    List<String> subjectAlternativeNames,
    @Nullable String commonName,
    @Nullable String peerAddress) {

  @Override
  public String toString() {
    return "{subject='%s', sans=%s, peer=%s}"
        .formatted(subject, subjectAlternativeNames, peerAddress == null ? "unknown" : peerAddress);
  }
}
