/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.tenant;

import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveRandomTenants;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveTenant;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.TenantMemberDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.filter.TenantMemberFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.sort.TenantMemberSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class TenantMemberIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldFindTenantMember(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantMemberDbReader reader = rdbmsService.getTenantMemberReader();

    createAndSaveRandomTenants(rdbmsWriter, b -> b);
    final var tenant = createAndSaveTenant(rdbmsWriter, b -> b);
    addUserToTenant(rdbmsWriter, tenant.tenantId(), "user-1");
    addUserToTenant(rdbmsWriter, tenant.tenantId(), "user-2");

    final var searchResult =
        reader.search(
            new TenantMemberQuery(
                TenantMemberFilter.of(
                    b -> b.memberType(EntityType.USER).tenantId(tenant.tenantId())),
                TenantMemberSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(TenantMemberEntity::id))
        .contains("user-1", "user-2");
  }

  private void addUserToTenant(
      final RdbmsWriter rdbmsWriter, final String tenantId, final String entityId) {
    rdbmsWriter.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, entityId, "USER"));
    rdbmsWriter.flush();
  }
}
