/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.es.schema.index.AuthorizationIndexES;
import io.camunda.optimize.service.db.es.schema.index.MappingRuleIndexES;
import io.camunda.optimize.service.db.es.schema.index.RoleIndexES;
import io.camunda.optimize.service.db.os.schema.index.AuthorizationIndexOS;
import io.camunda.optimize.service.db.os.schema.index.MappingRuleIndexOS;
import io.camunda.optimize.service.db.os.schema.index.RoleIndexOS;
import io.camunda.optimize.service.db.schema.BackupPriority;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.IndexLookupUtil;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccessStoreIndexMappingTest {

  @Test
  void shouldMapTheRoleFieldsOfTheOrchestrationClusterRoleIndex() {
    // when
    final Map<String, Property.Kind> kinds = kinds(new RoleIndexES());

    // then
    assertThat(kinds)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "roleId", Property.Kind.Keyword,
                "name", Property.Kind.Keyword,
                "description", Property.Kind.Text,
                "memberId", Property.Kind.Keyword,
                "memberType", Property.Kind.Keyword,
                "join", Property.Kind.Join));
  }

  @Test
  void shouldRelateRoleMembersToTheirRole() {
    // when
    final Property join =
        new RoleIndexES().addProperties(new TypeMapping.Builder()).build().properties().get("join");

    // then
    assertThat(join.join().relations()).containsExactly(Map.entry("role", List.of("member")));
  }

  @Test
  void shouldMapTheMappingRuleFieldsOfTheOrchestrationClusterMappingRuleIndex() {
    // when
    final Map<String, Property.Kind> kinds = kinds(new MappingRuleIndexES());

    // then
    assertThat(kinds)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "mappingRuleId", Property.Kind.Keyword,
                "claimName", Property.Kind.Keyword,
                "claimValue", Property.Kind.Keyword,
                "name", Property.Kind.Keyword));
  }

  @Test
  void shouldMapTheAuthorizationFieldsOfTheOrchestrationClusterAuthorizationIndex() {
    // when
    final Map<String, Property.Kind> kinds = kinds(new AuthorizationIndexES());

    // then
    assertThat(kinds)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "id", Property.Kind.Keyword,
                "ownerId", Property.Kind.Keyword,
                "ownerType", Property.Kind.Keyword,
                "resourceType", Property.Kind.Keyword,
                "resourceMatcher", Property.Kind.Short,
                "resourceId", Property.Kind.Keyword,
                "resourcePropertyName", Property.Kind.Keyword,
                "permissionTypes", Property.Kind.Keyword));
  }

  @Test
  void shouldBackUpAllAccessStoreIndicesInTheSameSnapshot() {
    // then memberships and grants are restored from the same point in time
    assertThat(List.of(new RoleIndexES(), new MappingRuleIndexES(), new AuthorizationIndexES()))
        .extracting(IndexMappingCreator::getBackupPriority)
        .containsOnly(BackupPriority.PRIORITY2);
  }

  @Test
  void shouldConvertTheAccessStoreIndicesBetweenElasticsearchAndOpenSearch() {
    // when
    final List<IndexMappingCreator> toOpenSearch =
        List.of(new RoleIndexES(), new MappingRuleIndexES(), new AuthorizationIndexES()).stream()
            .map(index -> IndexLookupUtil.convertIndexForDatabase(index, DatabaseType.OPENSEARCH))
            .toList();
    final List<IndexMappingCreator> toElasticsearch =
        List.of(new RoleIndexOS(), new MappingRuleIndexOS(), new AuthorizationIndexOS()).stream()
            .map(
                index -> IndexLookupUtil.convertIndexForDatabase(index, DatabaseType.ELASTICSEARCH))
            .toList();

    // then
    assertThat(toOpenSearch)
        .hasExactlyElementsOfTypes(
            RoleIndexOS.class, MappingRuleIndexOS.class, AuthorizationIndexOS.class);
    assertThat(toElasticsearch)
        .hasExactlyElementsOfTypes(
            RoleIndexES.class, MappingRuleIndexES.class, AuthorizationIndexES.class);
  }

  @Test
  void shouldStartAtVersionOne() {
    assertThat(new RoleIndexES().getVersion()).isEqualTo(1);
    assertThat(new MappingRuleIndexES().getVersion()).isEqualTo(1);
    assertThat(new AuthorizationIndexES().getVersion()).isEqualTo(1);
  }

  private static Map<String, Property.Kind> kinds(final DefaultIndexMappingCreator<?> index) {
    return index.addProperties(new TypeMapping.Builder()).build().properties().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()._kind()));
  }
}
