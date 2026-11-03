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
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a probe: evaluates its input as an input mapping would, authorizes the caller, and pushes
 * the probe to a worker. The client's request is kept with the probe and answered when it ends.
 */
public final class ProbeCreateProcessor implements TypedRecordProcessor<ProbeRecord> {

  /** Used when the request carries no timeout; the gateway normally sends the effective one. */
  static final long DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15).toMillis();

  static final long MAX_REQUEST_TIMEOUT = Duration.ofSeconds(30).toMillis();

  private static final String TENANT_NOT_ASSIGNED_MESSAGE =
      "Expected to create a probe for tenant '%s', but the user is not assigned to this tenant.";

  private final ProbeBehavior probeBehavior;
  private final ProbeTimeoutCheckScheduler timeoutChecker;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final ExpressionLanguage expressionLanguage;
  private final ExpressionProcessor inputExpressionProcessor;
  private final ClusterVariableJobSecretResolver clusterVariableSecretResolver;
  private final CslAuthorizationCheck cslCheck;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final InstantSource clock;

  public ProbeCreateProcessor(
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProbeBehavior probeBehavior,
      final ProbeTimeoutCheckScheduler timeoutChecker,
      final AsyncRequestBehavior asyncRequestBehavior,
      final ExpressionLanguage expressionLanguage,
      final ExpressionProcessor inputExpressionProcessor,
      final CslAuthorizationCheck cslCheck,
      final InstantSource clock) {
    this.probeBehavior = probeBehavior;
    this.timeoutChecker = timeoutChecker;
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.expressionLanguage = expressionLanguage;
    this.inputExpressionProcessor = inputExpressionProcessor;
    clusterVariableSecretResolver =
        new ClusterVariableJobSecretResolver(state.getClusterVariableState());
    this.cslCheck = cslCheck;
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
            .setRequestTimeout(value.getRequestTimeout());

    final var result =
        validate(probe)
            .flatMap(ignored -> authorizeCreate(command, probe))
            .flatMap(this::parseInput)
            .flatMap(input -> detectSecrets(input, probe).map(secrets -> new Input(input, secrets)))
            .flatMap(input -> authorizeSecrets(command, input))
            .flatMap(input -> evaluate(input, probe));
    if (result.isLeft()) {
      reject(command, result.getLeft());
      return;
    }

    // no check for a worker here: one may start polling before the deadline, which alone decides
    // NO_WORKER (a probe that no poll collected in time)
    final long probeKey = keyGenerator.nextKey();
    if (probe.getRequestTimeout() == 0) {
      probe.setRequestTimeout(DEFAULT_REQUEST_TIMEOUT);
    }
    probe.setDeadline(clock.millis() + probe.getRequestTimeout());
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.CREATED, probe);
    asyncRequestBehavior.writeAsyncRequestReceived(probeKey, command);
    timeoutChecker.schedule(probe.getDeadline());
    probeBehavior.notifyProbesAvailable(probe.getProbeId());
  }

  private Either<Rejection, ProbeRecord> validate(final ProbeRecord probe) {
    if (probe.getProbeId().isBlank()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT, "Expected a probe ID, but none was given."));
    }
    if (probe.getRequestTimeout() < 0 || probe.getRequestTimeout() > MAX_REQUEST_TIMEOUT) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected a request timeout between 0 and %d ms, but was %d."
                  .formatted(MAX_REQUEST_TIMEOUT, probe.getRequestTimeout())));
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

  private Either<Rejection, Expression> parseInput(final ProbeRecord probe) {
    final var expression = expressionLanguage.parseExpression(probe.getInputExpression());
    if (!(expression instanceof FeelExpression) || !expression.isValid()) {
      final var reason =
          expression.isValid() ? "it is not an expression" : expression.getFailureMessage();
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected the input to be a FEEL expression that evaluates to a context, but %s."
                  .formatted(reason)));
    }
    if (SecretReference.hasImpreciseReference(expression)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected every secret reference in the input to be the whole value of a context"
                  + " entry, but one is inside a list or a conditional context."));
    }
    return Either.right(expression);
  }

  /**
   * Detects the secrets the input references, keyed by the JSON pointer of the leaf they belong to,
   * relative to the input context: directly as {@code camunda.secrets.<name>}, or through a cluster
   * variable that holds secret references. Mirrors what the deployment does for input mappings,
   * with the whole input as the mapping target.
   */
  private Either<Rejection, Map<String, Set<SecretReference>>> detectSecrets(
      final Expression input, final ProbeRecord probe) {
    final var secretsByPointer = new LinkedHashMap<String, Set<SecretReference>>();
    for (final var detected : SecretReference.parse(input)) {
      secretsByPointer
          .computeIfAbsent(toJsonPointer(detected.path()), key -> new LinkedHashSet<>())
          .add(detected.secret());
    }
    final var clusterVariablesByPointer =
        new LinkedHashMap<String, Set<ClusterVariableReference>>();
    for (final var detected : ClusterVariableReference.parse(input)) {
      clusterVariablesByPointer
          .computeIfAbsent(toJsonPointer(detected.path()), key -> new LinkedHashSet<>())
          .add(detected.clusterVariable());
    }
    clusterVariableSecretResolver.resolveInto(
        clusterVariablesByPointer, probe.getTenantId(), secretsByPointer);
    secretsByPointer.forEach(
        (pointer, secrets) ->
            secrets.forEach(
                secret -> probe.addSecretReference(secret.storeId(), secret.name(), pointer)));
    return Either.right(secretsByPointer);
  }

  private Either<Rejection, Input> authorizeSecrets(
      final TypedRecord<ProbeRecord> command, final Input input) {
    final var names = new LinkedHashSet<String>();
    input.secrets().values().forEach(secrets -> secrets.forEach(s -> names.add(s.name())));
    for (final var name : names) {
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

  private Either<Rejection, ProbeRecord> evaluate(final Input input, final ProbeRecord probe) {
    return inputExpressionProcessor
        .evaluateAnyExpression(input.expression(), -1, probe.getTenantId())
        .mapLeft(
            failure ->
                new Rejection(
                    RejectionType.INVALID_ARGUMENT,
                    "Failed to evaluate the input: " + failure.getMessage()))
        .flatMap(
            result -> {
              if (result.isFailure()) {
                return Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Failed to evaluate the input: " + result.getFailureMessage()));
              }
              if (result.getType() != ResultType.OBJECT) {
                return Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected the input to evaluate to a context, but was '%s'."
                            .formatted(result.getType())));
              }
              return Either.right(probe.setInput(result.toBuffer()));
            });
  }

  private void reject(final TypedRecord<ProbeRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }

  /**
   * As {@code VariableMappingTransformer#toJsonPointer}: RFC 6901, {@code ~} and {@code /} escaped.
   */
  private static String toJsonPointer(final List<String> segments) {
    final var pointer = new StringBuilder();
    for (final String segment : segments) {
      pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
    }
    return pointer.toString();
  }

  private record Input(Expression expression, Map<String, Set<SecretReference>> secrets) {}
}
