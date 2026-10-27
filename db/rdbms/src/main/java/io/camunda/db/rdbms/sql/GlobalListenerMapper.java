/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.GlobalListenerDbQuery;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import java.util.List;

public interface GlobalListenerMapper {

  void insert(GlobalListenerDbModel listener);

  void insertEventTypes(GlobalListenerDbModel listener);

  void update(GlobalListenerDbModel listener);

  void delete(GlobalListenerDbModel listener);

  void deleteEventTypes(GlobalListenerDbModel listener);

  Long count(GlobalListenerDbQuery filter);

  GlobalListenerDbModel get(String id);

  List<GlobalListenerDbModel> search(GlobalListenerDbQuery filter);
}
