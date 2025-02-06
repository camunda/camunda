/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usermanagement.UserEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class UserCreatedUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-user";
  private final UserCreatedUpdatedHandler underTest = new UserCreatedUpdatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USER);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(UserEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<UserRecordValue> userCreatedRecord =
        factory.generateRecordWithIntent(ValueType.USER, UserIntent.CREATED);
    final Record<UserRecordValue> userUpdatedRecord =
        factory.generateRecordWithIntent(ValueType.USER, UserIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(userCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(userUpdatedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<UserRecordValue> userRecord =
        factory.generateRecordWithIntent(ValueType.USER, UserIntent.DELETED);

    // when
    final var idList = underTest.generateIds(userRecord);

    // then
    assertThat(idList).containsExactly(userRecord.getValue().getUsername());
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long recordKey = 123L;

    final UserRecordValue userRecordValue =
        ImmutableUserRecordValue.builder()
            .from(factory.generateObject(UserRecordValue.class))
            .withName("updated-foo")
            .withUsername("updated-bar")
            .withPassword("updated-baz")
            .withEmail("updated-baz@foo")
            .withUserKey(recordKey)
            .build();

    final Record<UserRecordValue> userRecord =
        factory.generateRecord(
            ValueType.USER,
            r -> r.withIntent(UserIntent.CREATED).withValue(userRecordValue).withKey(recordKey));

    // when
    final UserEntity userEntity =
        new UserEntity().setName("foo").setUsername("bar").setEmail("baz").setPassword("baz");
    underTest.updateEntity(userRecord, userEntity);

    // then
    assertThat(userEntity.getName()).isEqualTo("updated-foo");
    assertThat(userEntity.getUsername()).isEqualTo("updated-bar");
    assertThat(userEntity.getEmail()).isEqualTo("updated-baz@foo");
    assertThat(userEntity.getPassword()).isEqualTo("updated-baz");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final UserEntity inputEntity = new UserEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
