/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.db.rdbms.write.domain.JobDbModel.Builder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class JobDbModelTest {

  @Test
  void shouldTruncateErrorMessage() {
    final JobDbModel truncatedMessage =
        new JobDbModel.Builder()
            .errorMessage("errorMessage")
            .jobKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .elementInstanceKey(4L)
            .elementId("elementId")
            .type("type")
            .retries(1)
            .worker("worker")
            .deadline(OffsetDateTime.now())
            .tenantId("tenantId")
            .build()
            .truncateErrorMessage(10, null);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(10);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("errorMessa");
  }

  @Test
  void shouldTruncateErrorMessageBytes() {
    final JobDbModel truncatedMessage =
        new Builder()
            .errorMessage("ääääääääää")
            .jobKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .elementInstanceKey(4L)
            .elementId("elementId")
            .type("type")
            .retries(1)
            .worker("worker")
            .deadline(OffsetDateTime.now())
            .tenantId("tenantId")
            .build()
            .truncateErrorMessage(99, 5);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(2);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("ää");
  }

  @Test
  void shouldNotFailOnTruncateErrorMessageIfNoMessageIsSet() {
    final var jobDbModel =
        new Builder()
            .jobKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .elementInstanceKey(4L)
            .elementId("elementId")
            .type("type")
            .retries(1)
            .worker("worker")
            .deadline(OffsetDateTime.now())
            .tenantId("tenantId")
            .build();

    assertThatCode(() -> jobDbModel.truncateErrorMessage(10, 5)).doesNotThrowAnyException();
  }

  @Test
  void shouldPreserveRawCustomHeadersJsonByteForByteThroughQueueMerge() {
    // given — a DB-hydrated instance whose stored JSON is non-canonically formatted (spaced out,
    // key order Jackson would never itself produce for this record)
    final var nonCanonicalJson = "{ \"b\": \"2\", \"a\": \"1\" }";
    final var original = new Builder(nonCanonicalJson).jobKey(1L).build();
    final var queueItem =
        new QueueItem(ContextType.JOB, WriteStatementType.INSERT, 1L, "statement", original);
    final Function<Builder, Builder> mergeFunction = b -> b.worker("new-worker");
    final var merger = new UpsertMerger<>(ContextType.JOB, 1L, JobDbModel.class, mergeFunction);

    // when — coalesce a change to an unrelated field, never touching customHeaders()
    final var merged = (JobDbModel) merger.merge(queueItem).parameter();

    // then — the raw JSON is carried through completely unparsed, not reserialized
    assertThat(merged.serializedCustomHeaders()).isEqualTo(nonCanonicalJson);
    assertThat(merged.worker()).isEqualTo("new-worker");
  }
}
