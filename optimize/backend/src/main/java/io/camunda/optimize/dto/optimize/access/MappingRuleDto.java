/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.access;

import static io.camunda.optimize.service.db.schema.index.MappingRuleIndex.CLAIM_NAME;
import static io.camunda.optimize.service.db.schema.index.MappingRuleIndex.CLAIM_VALUE;
import static io.camunda.optimize.service.db.schema.index.MappingRuleIndex.MAPPING_RULE_ID;
import static io.camunda.optimize.service.db.schema.index.MappingRuleIndex.NAME;

import java.util.HashMap;
import java.util.Map;

public record MappingRuleDto(
    String mappingRuleId, String claimName, String claimValue, String name) {

  public Map<String, Object> toDocument() {
    final Map<String, Object> document = new HashMap<>();
    document.put(MAPPING_RULE_ID, mappingRuleId);
    document.put(CLAIM_NAME, claimName);
    document.put(CLAIM_VALUE, claimValue);
    document.put(NAME, name);
    return document;
  }

  public static MappingRuleDto fromDocument(final Map<String, Object> document) {
    return new MappingRuleDto(
        (String) document.get(MAPPING_RULE_ID),
        (String) document.get(CLAIM_NAME),
        (String) document.get(CLAIM_VALUE),
        (String) document.get(NAME));
  }
}
