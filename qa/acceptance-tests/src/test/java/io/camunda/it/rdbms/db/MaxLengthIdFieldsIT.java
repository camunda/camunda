/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MaxLengthIdFieldsIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldStoreMaxLengthProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();

    final var maxLengthId =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var processDefinition =
        ProcessDefinitionFixtures.createRandomized(b -> b.processDefinitionId(maxLengthId));
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var result = processDefinitionReader.findOne(processDefinition.processDefinitionKey());
    assertThat(result).isPresent();
    assertThat(result.get().processDefinitionId()).isEqualTo(maxLengthId);
  }

  @TestTemplate
  public void shouldStoreMaxLengthFormId(final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var formReader = rdbmsService.getFormReader();

    final var maxLengthId =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var form =
        new FormDbModel.FormDbModelBuilder()
            .formKey(123L)
            .formId(maxLengthId)
            .version(1L)
            .schema("schema")
            .tenantId("<default>")
            .build();
    rdbmsWriter.getFormWriter().create(form);
    rdbmsWriter.flush();

    final var result = formReader.findOne(123L);
    assertThat(result).isPresent();
    assertThat(result.get().formId()).isEqualTo(maxLengthId);
  }

  @TestTemplate
  public void shouldStoreMaxLengthTenantId(final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var tenantReader = rdbmsService.getTenantReader();

    final var maxLengthId =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var tenant =
        new TenantDbModel.Builder()
            .tenantKey(123L)
            .tenantId(maxLengthId)
            .name("Test Tenant")
            .description("Test tenant with max length ID")
            .build();
    rdbmsWriter.getTenantWriter().create(tenant);
    rdbmsWriter.flush();

    final var result = tenantReader.findOne(maxLengthId);
    assertThat(result).isPresent();
    assertThat(result.get().tenantId()).isEqualTo(maxLengthId);
  }

  @TestTemplate
  public void shouldStoreMaxLengthUsername(final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var userReader = rdbmsService.getUserReader();

    final var maxLengthUsername =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var user =
        new UserDbModel.Builder()
            .username(maxLengthUsername)
            .name("Test User")
            .email("test@example.com")
            .password("password")
            .build();
    rdbmsWriter.getUserWriter().create(user);
    rdbmsWriter.flush();

    final var result = userReader.findOneByUsername(maxLengthUsername);
    assertThat(result).isPresent();
    assertThat(result.get().username()).isEqualTo(maxLengthUsername);
  }

  @TestTemplate
  public void shouldStoreMaxLengthRoleId(final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var roleReader = rdbmsService.getRoleReader();

    final var maxLengthId =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var role =
        new RoleDbModel.Builder()
            .roleId(maxLengthId)
            .name("Test Role")
            .description("Test role with max length ID")
            .build();
    rdbmsWriter.getRoleWriter().create(role);
    rdbmsWriter.flush();

    final var result = roleReader.findOne(maxLengthId);
    assertThat(result).isPresent();
    assertThat(result.get().roleId()).isEqualTo(maxLengthId);
  }

  @TestTemplate
  public void shouldStoreMaxLengthGroupId(final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var groupReader = rdbmsService.getGroupReader();

    final var maxLengthId =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var group =
        new GroupDbModel.Builder()
            .groupId(maxLengthId)
            .name("Test Group")
            .description("Test group with max length ID")
            .build();
    rdbmsWriter.getGroupWriter().create(group);
    rdbmsWriter.flush();

    final var result = groupReader.findOne(maxLengthId);
    assertThat(result).isPresent();
    assertThat(result.get().groupId()).isEqualTo(maxLengthId);
  }

  @TestTemplate
  public void shouldStoreMaxLengthMappingRuleId(final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var mappingRuleReader = rdbmsService.getMappingRuleReader();

    final var maxLengthId =
        RandomStringUtils.insecure().nextAlphanumeric(vendorDatabaseProperties.varcharIndexSize());

    final var mappingRule =
        new MappingRuleDbModel.MappingRuleDbModelBuilder()
            .mappingRuleKey(123L)
            .mappingRuleId(maxLengthId)
            .claimName("test")
            .claimValue("value")
            .name("Test Mapping Rule")
            .build();
    rdbmsWriter.getMappingRuleWriter().create(mappingRule);
    rdbmsWriter.flush();

    final var result = mappingRuleReader.findOne(maxLengthId);
    assertThat(result).isPresent();
    assertThat(result.get().mappingRuleId()).isEqualTo(maxLengthId);
  }
}
