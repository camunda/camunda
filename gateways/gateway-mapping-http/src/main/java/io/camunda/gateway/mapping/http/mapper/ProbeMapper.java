/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantIds;
import static io.camunda.service.exception.ServiceException.Status.INVALID_ARGUMENT;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.ProbeRequestValidator;
import io.camunda.gateway.protocol.model.ActivatedProbeResult;
import io.camunda.gateway.protocol.model.ProbeActivationRequest;
import io.camunda.gateway.protocol.model.ProbeAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeCreationRequest;
import io.camunda.gateway.protocol.model.ProbeErrorAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeErrorResult;
import io.camunda.gateway.protocol.model.ProbeOkAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeOkResult;
import io.camunda.gateway.protocol.model.ProbeResult;
import io.camunda.gateway.protocol.model.ProbeUnsupportedAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeUnsupportedResult;
import io.camunda.gateway.protocol.model.TenantFilterEnum;
import io.camunda.service.ProbeServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Maps the probe endpoints (spec section 4) to {@link ProbeServices} and back. */
@NullMarked
public final class ProbeMapper {

  /** The title of a {@code 413}: the request does not fit in one log batch (D53). */
  public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";

  public static final String PROBE_TOO_LARGE =
      "The probe is too large to be written to the log. Shorten its input, then retry.";

  public static final String ANSWER_TOO_LARGE =
      "The answer is too large to be written to the log. Answer with a short ERROR instead.";

  private static final HttpStatusCode CONTENT_TOO_LARGE = HttpStatusCode.valueOf(413);

  private ProbeMapper() {}

  public static Either<ProblemDetail, ProbeServices.ProbeCreateRequest> toProbeCreateRequest(
      final ProbeCreationRequest request, final boolean multiTenancyEnabled) {
    return RequestMapper.getResult(
            ProbeRequestValidator.validateProbeCreationRequest(request), () -> request)
        .flatMap(
            valid -> validateTenantId(request.getTenantId(), multiTenancyEnabled, "Create Probe"))
        .map(
            tenantId ->
                new ProbeServices.ProbeCreateRequest(
                    request.getProbeId(),
                    request.getInput(),
                    tenantId,
                    request.getRequestTimeout()));
  }

  public static Either<ProblemDetail, ProbeServices.ProbeActivationRequest>
      toProbeActivationRequest(
          final ProbeActivationRequest request, final boolean multiTenancyEnabled) {
    final TenantFilter tenantFilter = toTenantFilter(request.getTenantFilter());
    return RequestMapper.getResult(
            ProbeRequestValidator.validateProbeActivationRequest(request), () -> request)
        .flatMap(valid -> resolveTenantIds(request, tenantFilter, multiTenancyEnabled))
        .map(
            tenantIds ->
                new ProbeServices.ProbeActivationRequest(
                    List.copyOf(request.getProbeIds()),
                    request.getWorker() == null ? "" : request.getWorker(),
                    request.getMaxProbesToActivate(),
                    request.getRequestTimeout(),
                    tenantIds,
                    tenantFilter));
  }

  public static Either<ProblemDetail, ProbeServices.ProbeAnswerRequest> toProbeAnswerRequest(
      final ProbeAnswerRequest request, final long probeKey) {
    return RequestMapper.getResult(
        ProbeRequestValidator.validateProbeAnswerRequest(request),
        () ->
            switch (request) {
              case final ProbeOkAnswerRequest ok ->
                  new ProbeServices.ProbeAnswerRequest(
                      probeKey, ProbeOutcome.OK, ok.getData(), null);
              case final ProbeUnsupportedAnswerRequest unsupported ->
                  new ProbeServices.ProbeAnswerRequest(
                      probeKey, ProbeOutcome.UNSUPPORTED, null, null);
              case final ProbeErrorAnswerRequest error ->
                  new ProbeServices.ProbeAnswerRequest(
                      probeKey, ProbeOutcome.ERROR, null, error.getMessage());
              default ->
                  throw new IllegalStateException(
                      "Expected an answer of outcome OK, UNSUPPORTED or ERROR, but got " + request);
            });
  }

  /**
   * The answer envelope (D9, D59), or a problem named after the reason the probe ended without an
   * answer (section 4): a probe that no worker collected is {@code 404}, one whose secrets failed
   * is {@code 400}, and one that got no answer in time is {@code 504} (D62). The detail is the
   * engine's message, which never holds a secret value.
   */
  public static Either<ProblemDetail, ProbeResult> toProbeResult(
      final ProbeServices.ProbeResult result) {
    final ProbeRecord probe = result.probe();
    final ProbeEndReason endReason = probe.getEndReason();
    return switch (endReason) {
      case UNSPECIFIED -> toAnswer(probe);
      case NO_WORKER -> Either.left(problem(HttpStatus.NOT_FOUND, probe, endReason));
      case SECRET_RESOLUTION_ERROR ->
          Either.left(problem(HttpStatus.BAD_REQUEST, probe, endReason));
      case WORKER_DID_NOT_ANSWER, SECRET_RESOLUTION_TIMEOUT ->
          Either.left(problem(HttpStatus.GATEWAY_TIMEOUT, probe, endReason));
    };
  }

  /**
   * The probes of a poll. Their input holds the secret values the engine injected; {@code
   * secretPointers} lists where, from the references the engine found at {@code CREATE} (D73), so a
   * worker can keep these values out of its logs. Never the names of the secrets.
   */
  public static List<ActivatedProbeResult> toActivatedProbes(
      final ProbeBatchRecord batch, final String physicalTenantId) {
    final List<ActivatedProbeResult> probes = new ArrayList<>();
    final Iterator<LongValue> keys = batch.probeKeys().iterator();
    final Iterator<ProbeRecord> records = batch.probes().iterator();
    while (keys.hasNext() && records.hasNext()) {
      final long probeKey = keys.next().getValue();
      final ProbeRecord probe = records.next();
      probes.add(
          ActivatedProbeResult.Builder.create()
              .probeKey(String.valueOf(probeKey))
              .probeId(probe.getProbeId())
              .worker(probe.getWorker())
              .deadline(probe.getDeadline())
              .tenantId(probe.getTenantId())
              .physicalTenantId(physicalTenantId)
              .input(probe.getInput())
              .secretPointers(secretPointersOf(probe))
              .build());
    }
    return probes;
  }

  /**
   * Maps an error of {@code createProbe} or {@code answerProbe}: a request too large for one log
   * batch is {@code 413} for probes (D53), while {@link GatewayErrorMapper} keeps {@code 500} for
   * every other endpoint, jobs included. {@link ProbeServices} keeps the rejection as the cause of
   * its {@link ServiceException} to tell it apart.
   *
   * @param tooLargeDetail the detail of a {@code 413}
   */
  public static @Nullable ProblemDetail mapErrorToProblem(
      final Throwable error, final String tooLargeDetail) {
    if (GatewayErrorMapper.unwrapError(error) instanceof final ServiceException exception
        && exception.getCause() instanceof final BrokerRejectionException rejection
        && rejection.getRejection().type() == RejectionType.EXCEEDED_BATCH_RECORD_SIZE) {
      return GatewayErrorMapper.createProblemDetail(
          CONTENT_TOO_LARGE, tooLargeDetail, PAYLOAD_TOO_LARGE);
    }
    return GatewayErrorMapper.mapErrorToProblem(error);
  }

  private static Either<ProblemDetail, ProbeResult> toAnswer(final ProbeRecord probe) {
    final String worker = probe.getWorker();
    return switch (probe.getOutcome()) {
      case OK ->
          Either.right(
              ProbeOkResult.Builder.create()
                  .outcome(ProbeOutcome.OK.name())
                  .worker(worker)
                  .data(dataOf(probe))
                  .build());
      case UNSUPPORTED ->
          Either.right(
              ProbeUnsupportedResult.Builder.create()
                  .outcome(ProbeOutcome.UNSUPPORTED.name())
                  .worker(worker)
                  .build());
      case ERROR ->
          Either.right(
              ProbeErrorResult.Builder.create()
                  .outcome(ProbeOutcome.ERROR.name())
                  .worker(worker)
                  .message(probe.getMessage())
                  .build());
      case UNSPECIFIED ->
          Either.left(
              GatewayErrorMapper.createProblemDetail(
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  "Expected the probe for '%s' to end with an answer or a reason, but it has neither."
                      .formatted(probe.getProbeId()),
                  ServiceException.Status.INTERNAL.name()));
    };
  }

  /** The worker's data; an answer that carried no data has the record's empty document. */
  private static Map<String, Object> dataOf(final ProbeRecord probe) {
    final Map<String, Object> data = probe.getData();
    return data == null ? Map.of() : data;
  }

  private static ProblemDetail problem(
      final HttpStatus status, final ProbeRecord probe, final ProbeEndReason endReason) {
    return GatewayErrorMapper.createProblemDetail(status, probe.getMessage(), endReason.name());
  }

  /** The distinct paths of the probe's secret references, already JSON pointers (RFC 6901). */
  private static List<String> secretPointersOf(final ProbeRecord probe) {
    final Set<String> pointers = new LinkedHashSet<>();
    for (final JobSecretReference reference : probe.secretReferences()) {
      pointers.add(reference.getPath());
    }
    return List.copyOf(pointers);
  }

  private static TenantFilter toTenantFilter(final @Nullable TenantFilterEnum tenantFilter) {
    if (tenantFilter == null) {
      return TenantFilter.PROVIDED;
    }
    return switch (tenantFilter) {
      case ASSIGNED -> TenantFilter.ASSIGNED;
      case PROVIDED -> TenantFilter.PROVIDED;
    };
  }

  /** As for job activation: the assigned tenants come from the authentication, in the engine. */
  private static Either<ProblemDetail, List<String>> resolveTenantIds(
      final ProbeActivationRequest request,
      final TenantFilter tenantFilter,
      final boolean multiTenancyEnabled) {
    if (tenantFilter == TenantFilter.ASSIGNED) {
      if (!multiTenancyEnabled) {
        return Either.left(
            GatewayErrorMapper.createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Expected to handle request Activate Probes with ASSIGNED tenant filter, but multi-tenancy is disabled",
                INVALID_ARGUMENT.name()));
      }
      return Either.right(List.of());
    }
    final @Nullable List<String> tenantIds = request.getTenantIds();
    return validateTenantIds(
        tenantIds == null ? List.of() : tenantIds, multiTenancyEnabled, "Activate Probes");
  }
}
