/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.MessageSubscriptionDbQuery;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import java.util.List;

public interface MessageSubscriptionMapper extends HistoryCleanupMapper {

  void insert(MessageSubscriptionDbModel messageSubscription);

  void update(MessageSubscriptionDbModel messageSubscription);

  Long count(MessageSubscriptionDbQuery filter);

  List<MessageSubscriptionDbModel> search(MessageSubscriptionDbQuery filter);
}
