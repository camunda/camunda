/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.WaitStateDbQuery;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import java.util.List;

public interface WaitStateMapper {

  void insert(WaitStateDbModel waitState);

  void update(WaitStateDbModel waitState);

  void delete(Long waitStateKey);

  WaitStateDbModel findOne(Long waitStateKey);

  long count(WaitStateDbQuery query);

  List<WaitStateDbModel> search(WaitStateDbQuery query);
}
