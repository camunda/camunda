/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * This processor decorates processors where authorization checks are required, taking care of the
 * authorization checks for them. If the authorization check fails, the processor will reject the
 * command and write a rejection response back to the client.
 *
 * <p>This decorator should be used for processors that do not require a resource from the state to
 * determine which resourceIds it is permitted for.
 */
public final class AuthorizableDistributionProcessor<T extends UnifiedRecordValue>
    implements DistributedTypedRecordProcessor<T> {

  public static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";
  private final AuthorizationCheckBehavior authorizationCheckBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final Authorizable<T> delegate;

  public AuthorizableDistributionProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final EngineConfiguration engineConfig,
      final Authorizable<T> delegate) {
    authorizationCheckBehavior =
        new AuthorizationCheckBehavior(
            processingState.getAuthorizationState(), processingState.getUserState(), engineConfig);
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.delegate = delegate;
  }

  @Override
  public void processNewCommand(final TypedRecord<T> command) {
    final var authorizationRequest = delegate.getAuthorizationRequest();

    if (authorizationCheckBehavior.isAuthorized(command, authorizationRequest)) {
      delegate.processNewCommand(command);
    } else {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authorizationRequest.getPermissionType(), authorizationRequest.getResourceType());
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<T> command) {
    try {
      delegate.processDistributedCommand(command);
    } catch (final Exception e) {
      delegate.tryHandleError(command, e);
    }
  }

  @Override
  public ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
    return delegate.tryHandleError(command, error);
  }

  public interface Authorizable<T extends UnifiedRecordValue> {
    AuthorizationRequest<?> getAuthorizationRequest();

    void processNewCommand(final TypedRecord<T> command);

    void processDistributedCommand(final TypedRecord<T> command);

    /**
     * Try to handle an error that occurred during processing.
     *
     * @param command The command that was being processed when the error occurred
     * @param error The error that occurred, and the processor should attempt to handle
     * @return The type of the processing error. Default: {@link ProcessingError#UNEXPECTED_ERROR}.
     */
    default ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
      return ProcessingError.UNEXPECTED_ERROR;
    }
  }
}
