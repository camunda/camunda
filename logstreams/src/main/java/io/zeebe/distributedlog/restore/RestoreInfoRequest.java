/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore;

public interface RestoreInfoRequest {

  /** @return the requester's latest commit position on its local log */
  long getLatestLocalPosition();

  /** @return the requester's required backup commit position */
  long getBackupPosition();
}
