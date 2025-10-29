/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.util.Optional;

public interface MessageSubscriptionReader {
  Optional<MessageSubscriptionEntity> getMessageSubscriptionEntityByFlowNodeInstanceId(
      final String flowNodeInstanceId);
}
