/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.engine.processing.consent.ConsentConnections.AdmitRule;
import io.camunda.zeebe.engine.processing.consent.ConsentConnections.Connection;
import io.camunda.zeebe.engine.processing.consent.ConsentConnections.Operation;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.engine.state.immutable.ConsentState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobConsent;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.ConsentDecision;
import io.camunda.zeebe.protocol.record.value.ConsentGround;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The consent gate. It runs when a gated job is created and every time the job would be handed to a
 * worker, by polling or by pushing, and answers from engine state alone: a worker that holds a
 * gated job holds proof of consent.
 *
 * <p>Consent is live when the subject holds a grant that covers the request, or approved this
 * process instance for the value the job carries. Otherwise the job is parked until the subject
 * answers.
 */
public final class ConsentGate {

  /** The variable a handed-out job carries its proof in, and the credential below it. */
  public static final String CONSENT_VARIABLE = "consent";

  public static final String CREDENTIAL_PATH = "/" + CONSENT_VARIABLE + "/credential";
  public static final String CHANGE_VARIABLE = "change";

  private final ConsentState consentState;
  private final ConsentConnections connections;

  public ConsentGate(
      final ConsentState consentState, final ClusterVariableState clusterVariableState) {
    this.consentState = consentState;
    connections = new ConsentConnections(clusterVariableState);
  }

  public ConsentConnections connections() {
    return connections;
  }

  /** Returns on what the job may be handed out now, or {@link Basis#NONE} if it must wait. */
  public Basis basisFor(final JobRecord job) {
    final JobConsent consent = job.getConsent();
    if (!consent.isGated()) {
      return Basis.NOT_GATED;
    }
    final var operation =
        connections.findOperation(consent.getConnection(), consent.getOperation());
    if (operation.isLeft()) {
      // the connection or the operation was removed after the job was created: fail closed
      return Basis.NONE;
    }
    if (consent.getGround() == ConsentGround.RELATIONSHIP) {
      return Requester.isPerson(consent.getRequester()) ? Basis.RELATIONSHIP : Basis.NONE;
    }
    if (!operation.get().consentRequired()) {
      return Basis.NOT_REQUIRED;
    }
    if (grantCovers(job, operation.get())) {
      return Basis.GRANT;
    }
    if (approvalCovers(job, operation.get())) {
      return Basis.APPROVAL;
    }
    return Basis.NONE;
  }

  public boolean mustPark(final JobRecord job) {
    return basisFor(job) == Basis.NONE;
  }

  /**
   * A grant covers the requests of its subject. A request from anyone else counts under it only
   * when the operation declares a rule that admits the requester; otherwise the instance waits for
   * an approval, which the subject gives seeing who asks.
   */
  private boolean grantCovers(final JobRecord job, final Operation operation) {
    if (!operation.scope().allowsGrant()) {
      return false;
    }
    final JobConsent consent = job.getConsent();
    final Optional<ConsentRecord> grant =
        consentState.getGrant(
            consent.getSubject(), consent.getConnection(), consent.getOperation());
    if (grant.isEmpty()) {
      return false;
    }
    final String requester = consent.getRequester();
    if (requester.equals(consent.getSubject())) {
      return true;
    }
    return operation.admit().stream().anyMatch(rule -> admits(rule, requester, job));
  }

  private static boolean admits(final AdmitRule rule, final String requester, final JobRecord job) {
    return switch (rule.kind()) {
      // the worker resolves the path and denies when it does not end on the requester
      case RELATION -> Requester.isPerson(requester);
      case CLIENT -> requester.equals(Requester.CLIENT_PREFIX + rule.value());
      case PROCESS -> rule.value().equals(job.getBpmnProcessId());
    };
  }

  /** An approval covers its process instance, for the value the subject was shown. */
  private boolean approvalCovers(final JobRecord job, final Operation operation) {
    if (!operation.scope().allowsApproval()) {
      return false;
    }
    final JobConsent consent = job.getConsent();
    return consentState
        .getApproval(
            job.getProcessInstanceKey(),
            consent.getSubject(),
            consent.getConnection(),
            consent.getOperation())
        .filter(approval -> approval.getChange().equals(consent.getChange()))
        .isPresent();
  }

  /** The request a parked job makes of its subject. */
  public ConsentRecord requestOf(final long jobKey, final JobRecord job) {
    final JobConsent consent = job.getConsent();
    return new ConsentRecord()
        .setSubject(consent.getSubject())
        .setRequester(consent.getRequester())
        .setConnection(consent.getConnection())
        .setOperation(consent.getOperation())
        .setDecision(ConsentDecision.UNSPECIFIED)
        .setChange(consent.getChangeBuffer())
        .setJobKey(jobKey)
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setBpmnProcessId(job.getBpmnProcessId())
        .setElementId(job.getElementId())
        .setTenantId(job.getTenantId());
  }

  /**
   * Adds the proof to the variables of a job that is being handed out: the variable {@code change}
   * holds the value recorded when the job was created, whatever the instance variables hold by now,
   * and the variable {@code consent} says on what the job was handed out. A job whose connection
   * names a credential carries its secret placeholder at {@link #CREDENTIAL_PATH}; the secret
   * injection replaces it in the response only.
   */
  public void addProof(final JobRecord job, final Basis basis) {
    final JobConsent consent = job.getConsent();
    if (!consent.isGated()) {
      return;
    }
    final Map<String, Object> variables =
        new LinkedHashMap<>(MsgPackConverter.convertToMap(job.getVariablesBuffer()));
    final Map<String, Object> proof = new LinkedHashMap<>();
    proof.put("ground", consent.getGround().name());
    proof.put("basis", basis.name());
    proof.put("subject", consent.getSubject());
    proof.put("requester", consent.getRequester());
    proof.put("connection", consent.getConnection());
    proof.put("operation", consent.getOperation());
    if (!consent.getTarget().isEmpty()) {
      proof.put("target", consent.getTarget());
    }
    connections
        .findOperation(consent.getConnection(), consent.getOperation())
        .ifRight(
            operation -> {
              proof.put("relationship", operation.relationship());
              proof.put("preconditions", operation.preconditions());
              if (basis == Basis.GRANT && !consent.getRequester().equals(consent.getSubject())) {
                proof.put(
                    "admit",
                    operation.admit().stream()
                        .filter(rule -> rule.kind() == AdmitRule.Kind.RELATION)
                        .map(AdmitRule::value)
                        .toList());
              }
            });
    credentialPlaceholder(job).ifPresent(placeholder -> proof.put("credential", placeholder));
    variables.put(CONSENT_VARIABLE, proof);
    final Map<String, Object> change = consent.getChange();
    if (!change.isEmpty()) {
      variables.put(CHANGE_VARIABLE, change);
    }
    job.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
  }

  private static Optional<String> credentialPlaceholder(final JobRecord job) {
    return job.getSecretReferences().stream()
        .filter(reference -> CREDENTIAL_PATH.equals(reference.getPath()))
        .map(reference -> "camunda.secrets." + reference.getSecretReference())
        .findFirst();
  }

  /** Returns the connection that owns a secret the job references, for a job that is not gated. */
  public Optional<Connection> credentialOwnerOfUngatedJob(final List<String> secretNames) {
    for (final String name : secretNames) {
      final var owner = connections.findByCredential(name);
      if (owner.isPresent()) {
        return owner;
      }
    }
    return Optional.empty();
  }

  /** On what a job may be handed out. */
  public enum Basis {
    /** The task declares no consent. */
    NOT_GATED,
    /** The operation does not require consent. */
    NOT_REQUIRED,
    GRANT,
    APPROVAL,
    /** A worker checks the declared relationship before it acts. */
    RELATIONSHIP,
    /** Nothing allows the hand-out yet: the job waits for its subject. */
    NONE
  }
}
