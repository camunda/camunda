/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class MessageSubscriptionDbReader extends AbstractEntityReader<MessageSubscriptionEntity>
    implements MessageSubscriptionReader {

  public MessageSubscriptionDbReader() {
    super(null);
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> search(
      final MessageSubscriptionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Message Subscription search not implemented on RDBMS");
  }
}
