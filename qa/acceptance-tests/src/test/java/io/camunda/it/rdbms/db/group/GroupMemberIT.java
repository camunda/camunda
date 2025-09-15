/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.sort.GroupSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class GroupMemberIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldFindGroupMember(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupMemberDbReader reader = rdbmsService.getGroupMemberReader();

    GroupFixtures.createAndSaveRandomGroups(rdbmsWriter, b -> b);
    final var group = GroupFixtures.createAndSaveGroup(rdbmsWriter, b -> b);
    addUserToGroup(rdbmsWriter, group.groupId(), "user-1");
    addUserToGroup(rdbmsWriter, group.groupId(), "user-2");

    final var searchResult =
        reader.search(
            new GroupQuery(
                GroupFilter.of(b -> b.memberType(EntityType.USER).joinParentId(group.groupId())),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(GroupMemberEntity::id))
        .contains("user-1", "user-2");
  }

  private void addUserToGroup(
      final RdbmsWriter rdbmsWriter, final String groupId, final String entityId) {
    rdbmsWriter.getGroupWriter().addMember(new GroupMemberDbModel(groupId, entityId, "USER"));
    rdbmsWriter.flush();
  }
}
