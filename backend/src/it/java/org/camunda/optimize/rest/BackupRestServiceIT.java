/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class BackupRestServiceIT extends AbstractIT {

  @Test
  public void backupApiNotAvailableWhenNotInCCSM() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildTriggerBackupRequest(new BackupRequestDto("abackupid"))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }
}
