/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.FormDbQuery;
import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.search.entities.FormEntity;
import java.util.List;

public interface FormMapper {

  void insert(FormDbModel form);

  void update(FormDbModel form);

  Long count(FormDbQuery filter);

  List<FormEntity> search(FormDbQuery filter);
}
