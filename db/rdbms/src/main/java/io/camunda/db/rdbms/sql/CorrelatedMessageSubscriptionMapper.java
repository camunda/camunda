/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.CorrelatedMessageSubscriptionDbQuery;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import java.util.List;

public interface CorrelatedMessageSubscriptionMapper extends ProcessInstanceDependantMapper {

  Long count(CorrelatedMessageSubscriptionDbQuery query);

  void insert(CorrelatedMessageSubscriptionDbModel correlatedMessageSubscription);

  List<CorrelatedMessageSubscriptionDbModel> search(CorrelatedMessageSubscriptionDbQuery query);
}
