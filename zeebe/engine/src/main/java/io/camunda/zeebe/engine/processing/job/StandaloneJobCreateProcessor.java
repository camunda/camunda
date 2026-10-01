/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.ClusterVariableJobSecretResolver;
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.expression.ExpressionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Creates a standalone job: a job without a process instance, which a client creates to ask the
 * workers of a job type a question and to wait for their answer.
 *
 * <p>The command is not answered here. Its request is kept as an async request of the job, and
 * answered once a worker completes, fails or throws an error for the job (see {@link
 * StandaloneJobAnswerProcessor}), or once the job expires (see {@link
 * StandaloneJobExpireProcessor}).
 *
 * <p>The job's input is a FEEL expression that must evaluate to a context, which becomes the job's
 * variables. It is evaluated without any process scope, so it can only read cluster variables. Its
 * secret references, whether direct ({@code camunda.secrets.<name>}) or reached through a {@code
 * SECRET_REFERENCE} cluster variable, are stored on the job like those of an input mapping, so job
 * activation resolves and injects them the same way.
 */
public final class StandaloneJobCreateProcessor implements TypedRecordProcessor<JobRecord> {

  static final int DEFAULT_RETRIES = 1;

  private static final DirectBuffer EMPTY_VARIABLES = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;
  private final ExpressionLanguage expressionLanguage;
  private final ExpressionBehavior expressionBehavior;
  private final ClusterVariableJobSecretResolver clusterVariableSecretResolver;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final StandaloneJobExpiryCheckScheduler expiryChecker;
  private final JobProcessingMetrics jobMetrics;

  public StandaloneJobCreateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionLanguage expressionLanguage,
      final ExpressionBehavior expressionBehavior,
      final ClusterVariableState clusterVariableState,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final AsyncRequestBehavior asyncRequestBehavior,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final StandaloneJobExpiryCheckScheduler expiryChecker,
      final JobProcessingMetrics jobMetrics) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.expressionLanguage = expressionLanguage;
    this.expressionBehavior = expressionBehavior;
    clusterVariableSecretResolver = new ClusterVariableJobSecretResolver(clusterVariableState);
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.jobActivationBehavior = jobActivationBehavior;
    this.expiryChecker = expiryChecker;
    this.jobMetrics = jobMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final JobRecord request = command.getValue();
    validate(request)
        .flatMap(valid -> authorize(command, request))
        .flatMap(authorized -> evaluateInput(request))
        .ifRightOrLeft(
            job -> createJob(command, job),
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  private Either<Rejection, JobRecord> validate(final JobRecord request) {
    if (request.getType().isBlank()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to create a standalone job with a job type, but no type was given"));
    }
    if (request.getTimeout() <= 0) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to create a standalone job with a positive time to wait for an answer, but"
                  + " it was '%d'".formatted(request.getTimeout())));
    }
    return Either.right(request);
  }

  private Either<Rejection, JobRecord> authorize(
      final TypedRecord<JobRecord> command, final JobRecord request) {
    return cslCheck
        .check(
            command,
            JobAuthorizations.forCreator(request.getType()),
            request,
            AuthorizationRejectionMapper.noPrincipal())
        .flatMap(
            authorized ->
                tenantCheck.checkTenant(
                    command,
                    request.getTenantId(),
                    request,
                    new Rejection(
                        RejectionType.FORBIDDEN,
                        "Expected to create a standalone job for tenant '%s', but the user is not"
                                .formatted(request.getTenantId())
                            + " assigned to this tenant")));
  }

  /**
   * Evaluates the input expression into the job's variables and collects its secret references,
   * keyed by the JSON pointer of the variable they belong to.
   */
  private Either<Rejection, JobRecord> evaluateInput(final JobRecord request) {
    final var job = newJob(request);
    final String inputExpression = request.getInputExpression();
    if (inputExpression.isEmpty()) {
      job.setVariables(EMPTY_VARIABLES);
      return Either.right(job);
    }

    final Expression expression = expressionLanguage.parseExpression(inputExpression);
    if (!expression.isValid()) {
      return invalidInput(expression.getFailureMessage());
    }
    if (expression.isStatic()) {
      return invalidInput("it is not a FEEL expression; prefix it with '='");
    }
    if (SecretReference.hasImpreciseReference(expression)) {
      return invalidInput(
          "it references a secret inside a list or a conditional; reference secrets only as"
              + " values of a context");
    }

    return expressionBehavior
        .evaluateWithoutScope(expression, request.getTenantId())
        .flatMap(
            result -> {
              if (result.getType() != ResultType.OBJECT) {
                return invalidInput(
                    "it must evaluate to a context, but it evaluated to '%s'"
                        .formatted(result.getType()));
              }
              job.setVariables(result.toBuffer());
              addSecretReferences(job, expression);
              return Either.right(job);
            });
  }

  private void addSecretReferences(final JobRecord job, final Expression expression) {
    final Map<String, Set<SecretReference>> secretsByPointer = new LinkedHashMap<>();
    SecretReference.parse(expression)
        .forEach(
            detected ->
                secretsByPointer
                    .computeIfAbsent(toJsonPointer(detected.path()), key -> new LinkedHashSet<>())
                    .add(detected.secret()));

    final Map<String, Set<ClusterVariableReference>> clusterVariablesByPointer =
        new LinkedHashMap<>();
    ClusterVariableReference.parse(expression)
        .forEach(
            detected ->
                clusterVariablesByPointer
                    .computeIfAbsent(toJsonPointer(detected.path()), key -> new LinkedHashSet<>())
                    .add(detected.clusterVariable()));
    clusterVariableSecretResolver.resolveInto(
        clusterVariablesByPointer, job.getTenantId(), secretsByPointer);

    secretsByPointer.forEach(
        (pointer, secrets) ->
            secrets.forEach(
                secret -> job.addSecretReference(secret.storeId(), secret.name(), pointer)));
  }

  private void createJob(final TypedRecord<JobRecord> command, final JobRecord job) {
    final long jobKey = keyGenerator.nextKey();
    job.setExpiresAt(command.getTimestamp() + command.getValue().getTimeout());

    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CREATED, job);
    asyncRequestBehavior.writeAsyncRequestReceived(jobKey, command);
    jobActivationBehavior.publishWork(jobKey, job);
    jobMetrics.countJobEvent(JobAction.CREATED, JobKind.STANDALONE, job.getType());

    final long expiresAt = job.getExpiresAt();
    sideEffectWriter.appendSideEffect(() -> expiryChecker.scheduleExpiry(expiresAt));
  }

  private static JobRecord newJob(final JobRecord request) {
    final int retries = request.getRetries() > 0 ? request.getRetries() : DEFAULT_RETRIES;
    return new JobRecord()
        .setType(request.getTypeBuffer())
        .setJobKind(JobKind.STANDALONE)
        .setTenantId(request.getTenantId())
        .setCustomHeaders(request.getCustomHeadersBuffer())
        .setRetries(retries)
        .setPriority(request.getPriority());
  }

  private static Either<Rejection, JobRecord> invalidInput(final String reason) {
    return Either.left(
        new Rejection(
            RejectionType.INVALID_ARGUMENT,
            "Expected to create a standalone job with a valid input expression, but " + reason));
  }

  /** The RFC 6901 JSON pointer of the given path, as job activation expects it. */
  private static String toJsonPointer(final List<String> segments) {
    final var pointer = new StringBuilder();
    for (final String segment : segments) {
      pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
    }
    return pointer.toString();
  }
}
