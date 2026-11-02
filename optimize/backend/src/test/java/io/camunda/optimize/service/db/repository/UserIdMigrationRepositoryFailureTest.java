/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.repository.es.UserIdMigrationRepositoryES;
import io.camunda.optimize.service.db.repository.os.UserIdMigrationRepositoryOS;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserIdMigrationRepositoryFailureTest {

  private static final String EMAIL = "user@example.com";
  private static final String NEW_USER_ID = "auth0|new-identity";
  private static final OptimizeRuntimeException FAILURE_WITH_QUERY =
      new OptimizeRuntimeException("Could not create task with query [term: " + EMAIL + "]");

  @Mock private OptimizeElasticsearchClient esClient;
  @Mock private TaskRepositoryES taskRepository;
  @Mock private OptimizeOpenSearchClient osClient;

  @Test
  void shouldNotExposeTheUserIdWhenTheElasticsearchUpdateFails() {
    // given
    when(taskRepository.tryUpdateByQueryRequest(anyString(), any(), any(), any(String[].class)))
        .thenThrow(FAILURE_WITH_QUERY);
    final UserIdMigrationRepository repository =
        new UserIdMigrationRepositoryES(esClient, taskRepository);

    // when - then
    assertThatThrownBy(() -> repository.migrateUserId(EMAIL, NEW_USER_ID))
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessageContaining("u***@example.com")
        .hasMessageNotContaining(EMAIL)
        .hasNoCause();
  }

  @Test
  void shouldNotExposeTheUserIdWhenTheOpenSearchUpdateFails() {
    // given
    when(osClient.updateByQueryTask(anyString(), any(), any(), any(String[].class)))
        .thenThrow(FAILURE_WITH_QUERY);
    final UserIdMigrationRepository repository = new UserIdMigrationRepositoryOS(osClient);

    // when - then
    assertThatThrownBy(() -> repository.migrateUserId(EMAIL, NEW_USER_ID))
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessageContaining("u***@example.com")
        .hasMessageNotContaining(EMAIL)
        .hasNoCause();
  }
}
