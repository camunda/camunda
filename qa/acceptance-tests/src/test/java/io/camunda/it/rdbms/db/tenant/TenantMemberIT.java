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

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.TenantMemberDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.sort.TenantSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class TenantMemberIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsService rdbmsService;

  @Autowired private TenantMemberDbReader tenantMemberDbReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFindTenantMember() {
    createAndSaveRandomTenants(rdbmsWriter, b -> b);
    final var tenant = createAndSaveTenant(rdbmsWriter, b -> b);
    addUserToTenant(tenant.tenantId(), "user-1");
    addUserToTenant(tenant.tenantId(), "user-2");

    final var searchResult =
        tenantMemberDbReader.search(
            new TenantQuery(
                TenantFilter.of(b -> b.memberType(EntityType.USER).joinParentId(tenant.tenantId())),
                TenantSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(TenantMemberEntity::id))
        .contains("user-1", "user-2");
  }

  private void addUserToTenant(final String tenantId, final String entityId) {
    rdbmsWriter.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, entityId, "USER"));
    rdbmsWriter.flush();
  }
}
