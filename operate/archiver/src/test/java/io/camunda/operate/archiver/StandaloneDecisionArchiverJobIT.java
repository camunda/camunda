/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class StandaloneDecisionArchiverJobIT extends ArchiverJobIT {

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Override
  protected ArchiverJob createArchiveJob(final List<Integer> partitionIds) {
    return new StandaloneDecisionArchiverJob(
        getArchiver(),
        partitionIds,
        decisionInstanceTemplate,
        getMetrics(),
        getArchiverRepository());
  }

  @Test
  void shouldArchiveStandaloneDecisionInstance() throws Exception {
    withArchiverJob(
        job -> {
          final var decision = decisionInstance("2020-01-01T00:00:00+00:00", -1L);
          store(decisionInstanceTemplate, decision);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(decisionInstanceTemplate, decision.getId(), "2020-01-01");
        });
  }

  @Test
  void shouldNotArchiveDecisionInstanceBelongingToProcess() throws Exception {
    withArchiverJob(
        job -> {
          final var standalone = decisionInstance("2020-01-01T00:00:00+00:00", -1L);
          final var processOwned = decisionInstance("2020-01-01T00:00:00+00:00", 12345L);
          store(decisionInstanceTemplate, standalone);
          store(decisionInstanceTemplate, processOwned);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(decisionInstanceTemplate, standalone.getId(), "2020-01-01");
          verifyNotMoved(decisionInstanceTemplate, processOwned.getId());
        });
  }

  @Test
  void shouldNotArchiveRecentStandaloneDecision() throws Exception {
    withArchiverJob(
        job -> {
          final var recent = decisionInstance("2099-01-01T00:00:00+00:00", -1L);
          store(decisionInstanceTemplate, recent);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(0);

          verifyNotMoved(decisionInstanceTemplate, recent.getId());
        });
  }

  private DecisionInstanceEntity decisionInstance(
      final String evaluationDate, final long processInstanceKey) {
    final var entity = create(DecisionInstanceEntity::new);
    entity.setEvaluationDate(OffsetDateTime.parse(evaluationDate));
    entity.setProcessInstanceKey(processInstanceKey);
    entity.setPartitionId(PARTITION_ID);
    return entity;
  }
}
