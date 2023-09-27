/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

public class MigrateCollectionIndexIT extends AbstractUpgrade311IT {
  @Test
  public void updateDefaultTenantIdForCollectionScopesInC8_zeebeImportEnabled() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(true);
    executeBulk("steps/3.10/collections/310-collection-index-data.json");

    // when
    performUpgrade();

    // then
    assertC8CollectionScopeTenantMigration();
  }

  @Test
  public void updateDefaultTenantIdForCollectionScopesInC8_zeebeImportDisabledButZeebeImportDataPresent() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/import/310-position-based-import-index-data.json");
    executeBulk("steps/3.10/collections/310-collection-index-data.json");

    // when
    performUpgrade();

    // then
    assertC8CollectionScopeTenantMigration();
  }

  @Test
  public void updateDefaultTenantIdForCollectionScopesInC8_zeebeImportDisabledButZeebeInstanceDataPresent() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/instances/310-process-instance-index-zeebe-data.json");
    executeBulk("steps/3.10/collections/310-collection-index-data.json");

    // when
    performUpgrade();

    // then
    assertC8CollectionScopeTenantMigration();
  }

  @Test
  public void doNotUpdateDefaultTenantIdForCollectionScopesInC7() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/collections/310-collection-index-data.json");

    // when
    performUpgrade();

    // then no changes have been applied to tenantIds of collection scope entries
    final Map<String, List<List<String>>> scopeTenantsByCollectionId = getAllCollectionScopeTenantIdsByCollectionId();
    List<String> nullTenantList = new ArrayList<>();
    nullTenantList.add(null);
    assertThat(scopeTenantsByCollectionId).hasSize(4)
      .containsOnly(
        Map.entry("noScopeCollection", Collections.emptyList()),
        Map.entry("oneScopeCollection", List.of(nullTenantList)),
        Map.entry("twoScopeCollection", List.of(nullTenantList, nullTenantList)),
        Map.entry("c7NonNullScopeCollection", List.of(List.of("csm")))
      );
  }

  private Map<String, List<List<String>>> getAllCollectionScopeTenantIdsByCollectionId() {
    return getAllDocumentsOfIndexAs(new CollectionIndex().getIndexName(), CollectionDefinitionDto.class)
      .stream()
      .collect(toMap(
        CollectionDefinitionDto::getId,
        collection -> collection.getData().getScope().stream().map(CollectionScopeEntryDto::getTenants).toList()
      ));
  }

  private void assertC8CollectionScopeTenantMigration() {
    final Map<String, List<List<String>>> scopeTenantsByCollectionId = getAllCollectionScopeTenantIdsByCollectionId();
    assertThat(scopeTenantsByCollectionId).hasSize(4)
      .contains(Map.entry("noScopeCollection", Collections.emptyList()));
    scopeTenantsByCollectionId.remove("noScopeCollection");
    assertThat(scopeTenantsByCollectionId.values())
      .allSatisfy(tenantIdLists -> assertThat(tenantIdLists).isNotEmpty()
        .allSatisfy(tenantIds -> assertThat(tenantIds).containsExactly(ZEEBE_DEFAULT_TENANT_ID)
        ));
  }
}
