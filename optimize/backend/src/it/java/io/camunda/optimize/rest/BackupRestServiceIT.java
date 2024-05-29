/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.util.SuppressionConstants.UNUSED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractPlatformIT;
import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BackupRestServiceIT extends AbstractPlatformIT {
  private static final Long VALID_BACKUP_ID = 123L;

  @ParameterizedTest
  @MethodSource("backupApiRequestExecutorSuppliers")
  public void backupApiNotAvailableWhenNotInCCSM(
      final Supplier<OptimizeRequestExecutor> backupApiRequestExecutorSuppliers) {
    // when
    final Response response = backupApiRequestExecutorSuppliers.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @SuppressWarnings(UNUSED)
  private static Stream<Supplier<OptimizeRequestExecutor>> backupApiRequestExecutorSuppliers() {
    return Stream.of(
        () ->
            embeddedOptimizeExtension
                .getRequestExecutor()
                .buildTriggerBackupRequest(new BackupRequestDto(VALID_BACKUP_ID)),
        () ->
            embeddedOptimizeExtension
                .getRequestExecutor()
                .buildGetBackupStateRequest(VALID_BACKUP_ID),
        () ->
            embeddedOptimizeExtension
                .getRequestExecutor()
                .buildDeleteBackupRequest(VALID_BACKUP_ID));
  }
}
