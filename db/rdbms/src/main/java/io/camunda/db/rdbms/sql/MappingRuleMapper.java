/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.MappingRuleDbQuery;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.search.entities.MappingRuleEntity;
import java.util.List;

public interface MappingRuleMapper {

  void insert(MappingRuleDbModel form);

  Long count(MappingRuleDbQuery filter);

  List<MappingRuleEntity> search(MappingRuleDbQuery filter);

  void delete(String mappingRuleId);
}
