/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.Builder;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class UserTaskFixtures extends CommonFixtures {

  private UserTaskFixtures() {}

  public static UserTaskDbModel createRandomized() {
    return createRandomized(b -> b);
  }

  public static UserTaskDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var builder =
        new Builder()
            .userTaskKey(nextKey())
            .elementId(generateRandomString("flowNodeBpmnId"))
            .processDefinitionId(generateRandomString("processDefinitionId"))
            .processInstanceKey(nextKey())
            .creationDate(NOW)
            .completionDate(NOW.plusDays(1))
            .assignee("bud spencer")
            .state(UserTaskState.CREATED)
            .formKey(nextKey())
            .processDefinitionKey(nextKey())
            .processInstanceKey(nextKey())
            .elementInstanceKey(nextKey())
            .tenantId(generateRandomString("tenant"))
            .dueDate(NOW.plusDays(3))
            .followUpDate(NOW.plusDays(2))
            .candidateGroups(generateRandomStrings("group", 2))
            .candidateUsers(generateRandomStrings("user", 2))
            .externalFormReference(generateRandomString("externalFormReference"))
            .processDefinitionVersion(RANDOM.nextInt(100))
            .customHeaders(Map.of("key", "value"))
            .priority(RANDOM.nextInt(100));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomUserTasks(final RdbmsService rdbmsService) {
    createAndSaveRandomUserTasks(rdbmsService, nextStringId());
  }

  public static void createAndSaveRandomUserTasks(
      final RdbmsService rdbmsService, final Function<Builder, Builder> builderFunction) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(0L);
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getUserTaskWriter().create(UserTaskFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  /**
   * @param processDefinitionId ... will be used to have one scope id for the test
   */
  public static void createAndSaveRandomUserTasks(
      final RdbmsService rdbmsService, final String processDefinitionId) {
    createAndSaveRandomUserTasks(rdbmsService, b -> b.processDefinitionId(processDefinitionId));
  }

  public static RdbmsWriter createAndSaveUserTask(
      final RdbmsService rdbmsService, final UserTaskDbModel processInstance) {
    return createAndSaveUserTasks(rdbmsService.createWriter(1L), List.of(processInstance));
  }

  public static UserTaskDbModel createAndSaveUserTask(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var randomized = createRandomized(builderFunction);
    createAndSaveUserTasks(rdbmsWriter, List.of(randomized));
    return randomized;
  }

  public static RdbmsWriter createAndSaveUserTask(
      final RdbmsWriter rdbmsWriter, final UserTaskDbModel processInstance) {
    return createAndSaveUserTasks(rdbmsWriter, List.of(processInstance));
  }

  public static RdbmsWriter createAndSaveUserTasks(
      final RdbmsWriter rdbmsWriter, final List<UserTaskDbModel> processInstanceList) {
    for (final UserTaskDbModel processInstance : processInstanceList) {
      rdbmsWriter.getUserTaskWriter().create(processInstance);
    }
    rdbmsWriter.flush();
    return rdbmsWriter;
  }
}
