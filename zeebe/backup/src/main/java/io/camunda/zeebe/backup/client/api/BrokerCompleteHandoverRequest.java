/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import io.camunda.zeebe.protocol.management.BackupRequestType;

/**
 * Asks a partition to complete the handover from continuous backup to the log archive at the given
 * HANDOVER checkpoint. The backup id of the request is the id of that checkpoint.
 */
public final class BrokerCompleteHandoverRequest extends BackupDeleteRequest {

  public BrokerCompleteHandoverRequest() {
    request.setType(BackupRequestType.COMPLETE_HANDOVER);
  }

  @Override
  public String getType() {
    return "Backup#completeHandover";
  }
}
