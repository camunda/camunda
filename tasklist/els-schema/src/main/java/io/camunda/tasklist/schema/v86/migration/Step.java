/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.tasklist.schema.v86.SemanticVersion;
import java.time.OffsetDateTime;
import java.util.Comparator;

/**
 * A step describes a change in one index in a specific version and in which order inside the
 * version.<br>
 * A step stores when it was created and applied.<br>
 * The change is described in content of step.<br>
 * It also provides comparators for SemanticVersion and order comparing.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({@JsonSubTypes.Type(value = ProcessorStep.class)})
public interface Step {

  Comparator<Step> SEMANTICVERSION_COMPARATOR =
      Comparator.comparing(s -> SemanticVersion.fromVersion(s.getVersion()));

  Comparator<Step> ORDER_COMPARATOR = Comparator.comparing(Step::getOrder);

  Comparator<Step> SEMANTICVERSION_ORDER_COMPARATOR =
      (s1, s2) -> {
        int result = SEMANTICVERSION_COMPARATOR.compare(s1, s2);
        if (result == 0) {
          result = ORDER_COMPARATOR.compare(s1, s2);
        }
        return result;
      };

  String INDEX_NAME = "indexName",
      CREATED_DATE = "createdDate",
      APPLIED = "applied",
      APPLIED_DATE = "appliedDate",
      VERSION = "version",
      ORDER = "order",
      CONTENT = "content";

  OffsetDateTime getCreatedDate();

  Step setCreatedDate(final OffsetDateTime date);

  OffsetDateTime getAppliedDate();

  Step setAppliedDate(final OffsetDateTime date);

  String getVersion();

  Integer getOrder();

  boolean isApplied();

  Step setApplied(final boolean isApplied);

  String getIndexName();

  String getContent();

  String getDescription();
}
