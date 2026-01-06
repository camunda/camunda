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
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.filter.GroupMemberFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.sort.GroupMemberSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.OffsetDateTime;
import java.util.UUID;
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
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupMemberDbReader reader = rdbmsService.getGroupMemberReader();

    final var group = GroupFixtures.createAndSaveGroup(rdbmsWriters, b -> b);
    final var userid1 = "user-" + UUID.randomUUID();
    final var userid2 = "user-" + UUID.randomUUID();
    addUserToGroup(rdbmsWriters, group.groupId(), userid1);
    addUserToGroup(rdbmsWriters, group.groupId(), userid2);

    final var group2 = GroupFixtures.createAndSaveGroup(rdbmsWriters, b -> b);
    final var userid3 = "user-" + UUID.randomUUID();
    final var userid4 = "user-" + UUID.randomUUID();
    addUserToGroup(rdbmsWriters, group2.groupId(), userid3);
    addUserToGroup(rdbmsWriters, group2.groupId(), userid4);

    final var searchResult =
        reader.search(
            new GroupMemberQuery(
                GroupMemberFilter.of(b -> b.memberType(EntityType.USER).groupId(group.groupId())),
                GroupMemberSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(GroupMemberEntity::id))
        .containsExactlyInAnyOrder(userid1, userid2);
  }

  private void addUserToGroup(
      final RdbmsWriters rdbmsWriters, final String groupId, final String entityId) {
    rdbmsWriters.getGroupWriter().addMember(new GroupMemberDbModel(groupId, entityId, "USER"));
    rdbmsWriters.flush();
  }
}
