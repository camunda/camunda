/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ClusterVariableEntity.MetadataEntry;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.entities.ValueTypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ClusterVariableDbModelTest {

  @Test
  public void shouldSetCorrectTypeAndValueForNullValue() {
    // given
    final ClusterVariableDbModel.ClusterVariableDbModelBuilder builder =
        new ClusterVariableDbModel.ClusterVariableDbModelBuilder();

    // when
    final ClusterVariableDbModel model =
        builder
            .name("test")
            .value("null")
            .tenantId("tenant1")
            .scope(ClusterVariableScope.GLOBAL)
            .build();

    // then
    assertThat(model.type()).isEqualTo(ValueTypeEnum.NULL);
    assertThat(model.doubleValue()).isNull();
    assertThat(model.longValue()).isNull();
    assertThat(model.value()).isEqualTo("null");
    assertThat(model.metadata()).isEmpty();
  }

  @Test
  public void shouldSetCorrectTypeAndValueForLongValue() {
    // given
    final ClusterVariableDbModel.ClusterVariableDbModelBuilder builder =
        new ClusterVariableDbModel.ClusterVariableDbModelBuilder();

    // when
    final ClusterVariableDbModel model =
        builder
            .name("test")
            .value("123456")
            .tenantId("tenant1")
            .scope(ClusterVariableScope.GLOBAL)
            .build();

    // then
    assertThat(model.type()).isEqualTo(ValueTypeEnum.LONG);
    assertThat(model.doubleValue()).isNull();
    assertThat(model.longValue()).isEqualTo(123456L);
    assertThat(model.value()).isEqualTo("123456");
  }

  @Test
  public void shouldCopyClusterVariable() {
    // given
    final List<MetadataEntry> metadata =
        List.of(
            new MetadataEntry("kind", "CREDENTIAL", null),
            new MetadataEntry("schemaVersion", "2", 2.0));
    final ClusterVariableDbModel.ClusterVariableDbModelBuilder builder =
        new ClusterVariableDbModel.ClusterVariableDbModelBuilder();

    // when
    final ClusterVariableDbModel model =
        builder
            .name("test")
            .value("someValue")
            .tenantId("tenant1")
            .scope(ClusterVariableScope.GLOBAL)
            .metadata(metadata)
            .build();

    // then
    assertThat(model.metadata()).isEqualTo(metadata);

    // and copy() preserves metadata unless overridden
    final ClusterVariableDbModel copied = model.copy(b -> b);
    assertThat(copied.name()).isEqualTo("test");
    assertThat(copied.value()).isEqualTo("someValue");
    assertThat(copied.tenantId()).isEqualTo("tenant1");
    assertThat(copied.scope()).isEqualTo(ClusterVariableScope.GLOBAL);
    assertThat(copied.metadata()).isEqualTo(metadata);
  }
}
