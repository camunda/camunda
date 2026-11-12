/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.el.impl.FeelExpression;
import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.ClusterVariableJobSecretResolver;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Creates a probe. It checks the caller's permissions before it parses or evaluates the input, then
 * evaluates the input as an input mapping, finding its secret references with their JSON pointers.
 * The client's request is kept with the probe and answered when the probe ends.
 */
@NullMarked
public final class ProbeCreateProcessor implements TypedRecordProcessor<ProbeRecord> {

  static final int MAX_PROBE_ID_LENGTH = 256;
  static final long MAX_REQUEST_TIMEOUT = Duration.ofSeconds(30).toMillis();

  private static final String TENANT_NOT_ASSIGNED_MESSAGE =
      "Expected to create a probe for tenant '%s', but the user is not assigned to this tenant.";

  private final ExpressionLanguage expressionLanguage;
  private final ExpressionProcessor inputExpressionProcessor;
  private final ClusterVariableJobSecretResolver clusterVariableSecretResolver;
  private final CslAuthorizationCheck cslCheck;
  private final ProbeBehavior probeBehavior;
  private final ProbeTimeoutCheckScheduler timeoutChecker;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final InstantSource clock;

  public ProbeCreateProcessor(
      final ExpressionLanguage expressionLanguage,
      final ExpressionProcessor inputExpressionProcessor,
      final ClusterVariableState clusterVariableState,
      final CslAuthorizationCheck cslCheck,
      final ProbeBehavior probeBehavior,
      final ProbeTimeoutCheckScheduler timeoutChecker,
      final AsyncRequestBehavior asyncRequestBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final InstantSource clock) {
    this.expressionLanguage = expressionLanguage;
    this.inputExpressionProcessor = inputExpressionProcessor;
    clusterVariableSecretResolver = new ClusterVariableJobSecretResolver(clusterVariableState);
    this.cslCheck = cslCheck;
    this.probeBehavior = probeBehavior;
    this.timeoutChecker = timeoutChecker;
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    final var value = command.getValue();
    final var probe =
        new ProbeRecord()
            .setProbeId(value.getProbeId())
            .setTenantId(value.getTenantId())
            .setInputExpression(value.getInputExpression())
            .setRequestTimeout(value.getRequestTimeout())
            .setDeadline(value.getDeadline());

    // the order matters: a caller without PROBE:CREATE never makes the engine parse or evaluate
    final var result =
        validate(probe)
            .flatMap(ignored -> authorizeCreate(command, probe))
            .flatMap(ignored -> parseInput(probe))
            .flatMap(input -> authorizeSecrets(command, detectSecrets(input, probe), input))
            .flatMap(input -> evaluate(input, probe));
    if (result.isLeft()) {
      final var rejection = result.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final long probeKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.CREATED, probe);
    asyncRequestBehavior.writeAsyncRequestReceived(
        probeKey, StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED, command);

    // the gateway set the deadline when the request arrived; a CREATE that queued past it ends at
    // once, rather than leaving its client to the gateway's own time-out
    if (probe.getDeadline() <= clock.millis()) {
      probeBehavior.timeOut(probeKey, probe, ProbeEndReason.NO_WORKER);
      return;
    }
    timeoutChecker.schedule(probe.getDeadline());
    probeBehavior.notifyProbesAvailable(probe.getProbeId());
  }

  private Either<Rejection, ProbeRecord> validate(final ProbeRecord probe) {
    final var probeId = probe.getProbeId();
    if (probeId.isEmpty()
        || probeId.length() > MAX_PROBE_ID_LENGTH
        || probeId.chars().anyMatch(Character::isWhitespace)
        || "*".equals(probeId)) {
      return invalidArgument(
          "Expected a probe ID of 1 to %d characters, without whitespace and other than '*', but got '%s'."
              .formatted(MAX_PROBE_ID_LENGTH, probeId));
    }
    if (probe.getInputExpression().isBlank()) {
      return invalidArgument("Expected an input, but got a blank one.");
    }
    if (probe.getRequestTimeout() < 0 || probe.getRequestTimeout() > MAX_REQUEST_TIMEOUT) {
      return invalidArgument(
          "Expected a request timeout from 0 to %d ms, but got %d ms."
              .formatted(MAX_REQUEST_TIMEOUT, probe.getRequestTimeout()));
    }
    if (probe.getDeadline() <= 0) {
      return invalidArgument(
          "Expected a deadline set when the request was received, but got %d."
              .formatted(probe.getDeadline()));
    }
    return Either.right(probe);
  }

  private Either<Rejection, ProbeRecord> authorizeCreate(
      final TypedRecord<ProbeRecord> command, final ProbeRecord probe) {
    return cslCheck.checkAuthorizationAndTenant(
        command,
        RequiredAuthorization.of(
            b ->
                b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROBE))
                    .permissionType(AuthzModelMapper.fromProtocol(PermissionType.CREATE))
                    .resourceId(probe.getProbeId())),
        probe,
        AuthorizationRejectionMapper.noPrincipal(),
        probe.getTenantId(),
        new Rejection(
            RejectionType.FORBIDDEN, TENANT_NOT_ASSIGNED_MESSAGE.formatted(probe.getTenantId())));
  }

  /** Parses the input and rejects a reference that the deploy validator would reject too. */
  private Either<Rejection, Expression> parseInput(final ProbeRecord probe) {
    final var input = expressionLanguage.parseExpression(probe.getInputExpression());
    if (!input.isValid()) {
      return invalidArgument(
          "Expected the input to be a valid FEEL expression, but parsing failed: %s"
              .formatted(input.getFailureMessage()));
    }
    if (!(input instanceof FeelExpression)) {
      return invalidArgument(
          "Expected the input to be a FEEL expression starting with '=', but got '%s'."
              .formatted(probe.getInputExpression()));
    }
    if (SecretReference.hasImpreciseReference(input)) {
      return invalidArgument(
          "Expected each secret reference in the input to be the whole value of a context entry,"
              + " but one is inside a list or a context produced by a branch.");
    }
    return Either.right(input);
  }

  /**
   * Finds the secrets the input references, as input mappings do in two steps: directly as {@code
   * camunda.secrets.<name>} (found at deploy for jobs), or through a {@code SECRET_REFERENCE}
   * cluster variable (found at job creation). Adds them to the probe with the JSON pointer of their
   * leaf, relative to the input context, and returns their names.
   */
  private Set<String> detectSecrets(final Expression input, final ProbeRecord probe) {
    final var secretsByPointer = new LinkedHashMap<String, Set<SecretReference>>();
    for (final var detected : SecretReference.parse(input)) {
      secretsByPointer
          .computeIfAbsent(
              VariableMappingTransformer.toJsonPointer(detected.path()),
              pointer -> new LinkedHashSet<>())
          .add(detected.secret());
    }
    final var clusterVariablesByPointer =
        new LinkedHashMap<String, Set<ClusterVariableReference>>();
    for (final var detected : ClusterVariableReference.parse(input)) {
      clusterVariablesByPointer
          .computeIfAbsent(
              VariableMappingTransformer.toJsonPointer(detected.path()),
              pointer -> new LinkedHashSet<>())
          .add(detected.clusterVariable());
    }
    clusterVariableSecretResolver.resolveInto(
        clusterVariablesByPointer, probe.getTenantId(), secretsByPointer);

    final var names = new LinkedHashSet<String>();
    secretsByPointer.forEach(
        (pointer, secrets) ->
            secrets.forEach(
                secret -> {
                  probe.addSecretReference(secret.storeId(), secret.name(), pointer);
                  names.add(secret.name());
                }));
    return names;
  }

  /** The worker receives the secret values without any permission, so the client needs them. */
  private Either<Rejection, Expression> authorizeSecrets(
      final TypedRecord<ProbeRecord> command,
      final Set<String> secretNames,
      final Expression input) {
    for (final var name : secretNames) {
      final var result =
          cslCheck.check(
              command,
              RequiredAuthorization.of(
                  b ->
                      b.resourceType(
                              AuthzModelMapper.fromProtocol(AuthorizationResourceType.SECRET))
                          .permissionType(AuthzModelMapper.fromProtocol(PermissionType.READ))
                          .resourceId(name)),
              input,
              AuthorizationRejectionMapper.noPrincipal());
      if (result.isLeft()) {
        return result;
      }
    }
    return Either.right(input);
  }

  /**
   * Evaluates the input as an input mapping: a secret becomes its placeholder, cluster variables
   * resolve, and no process variable is visible.
   */
  private Either<Rejection, ProbeRecord> evaluate(final Expression input, final ProbeRecord probe) {
    return inputExpressionProcessor
        .evaluateAnyExpression(input, -1, probe.getTenantId())
        .mapLeft(
            failure ->
                new Rejection(
                    RejectionType.INVALID_ARGUMENT,
                    "Expected the input to evaluate to a context, but the evaluation failed: "
                        + failure.getMessage()))
        .flatMap(
            result ->
                result.getType() == ResultType.OBJECT
                    ? Either.right(probe.setInput(result.toBuffer()))
                    : invalidArgument(
                        "Expected the input to evaluate to a context, but got '%s'."
                            .formatted(result.getType())));
  }

  private static <T> Either<Rejection, T> invalidArgument(final String reason) {
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }
}
