/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.DecisionInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
public class StandaloneDecisionArchiverJobIT extends ArchiverJobIT<StandaloneDecisionArchiverJob> {
  private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

  @Override
  StandaloneDecisionArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {

    final var dependantTemplates =
        resourceProvider.getIndexTemplateDescriptors().stream()
            .filter(DecisionInstanceDependant.class::isInstance)
            .map(DecisionInstanceDependant.class::cast)
            .toList();

    return new StandaloneDecisionArchiverJob(
        repository,
        resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class),
        exporterMetrics,
        LOGGER,
        executor,
        dependantTemplates);
  }

  @TestTemplate
  void shouldArchiveStandaloneDecisionInstance(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var decisionInstanceTemplate =
              resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);

          final var decisionInstance = decisionInstance("2020-01-01T00:00:00+00:00");
          store(decisionInstanceTemplate, client, decisionInstance);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(decisionInstanceTemplate, client, decisionInstance, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldNotArchiveDecisionInstanceWithProcessInstanceKey(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - a decision instance that belongs to a process instance (not standalone)
          final var decisionInstanceTemplate =
              resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);

          final var standaloneDecision = decisionInstance("2020-01-01T00:00:00+00:00");
          final var processDecision =
              decisionInstance("2020-01-01T00:00:00+00:00")
                  // non-negative means it belongs to a process instance
                  .setProcessInstanceKey(12345L);

          store(decisionInstanceTemplate, client, standaloneDecision);
          store(decisionInstanceTemplate, client, processDecision);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - only the standalone decision should be archived
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(decisionInstanceTemplate, client, standaloneDecision, "2020-01-01");
          verifyNotMoved(decisionInstanceTemplate, client, processDecision);
        });
  }

  @TestTemplate
  void shouldNotArchiveRecentStandaloneDecisionInstance(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - a decision instance evaluated very recently (should not be archived yet)
          final var decisionInstanceTemplate =
              resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);

          final var oldDecision = decisionInstance("2020-01-01T00:00:00+00:00");
          final var recentDecision = decisionInstance("2099-01-01T00:00:00+00:00");

          store(decisionInstanceTemplate, client, oldDecision);
          store(decisionInstanceTemplate, client, recentDecision);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(decisionInstanceTemplate, client, oldDecision, "2020-01-01");
          verifyNotMoved(decisionInstanceTemplate, client, recentDecision);
        });
  }

  @TestTemplate
  void shouldArchiveStandaloneDecisionInstanceWithDependantAuditLog(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var decisionInstanceTemplate =
              resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

          final var decisionInstance = decisionInstance("2020-01-01T00:00:00+00:00");

          final var auditLogEntry = new AuditLogEntity();
          auditLogEntry.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
          auditLogEntry.setEntityKey(decisionInstance.getId());
          auditLogEntry.setEntityType(AuditLogEntityType.DECISION);

          store(decisionInstanceTemplate, client, decisionInstance);
          store(auditLogTemplate, client, auditLogEntry);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(decisionInstanceTemplate, client, decisionInstance, "2020-01-01");
          verifyMoved(auditLogTemplate, client, auditLogEntry, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldNotArchiveAuditLogWithDifferentEntityType(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - an audit log entry for a different entity type should not be archived
          final var decisionInstanceTemplate =
              resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

          final var decisionInstance = decisionInstance("2020-01-01T00:00:00+00:00");

          final var unrelatedAuditLog = new AuditLogEntity();
          unrelatedAuditLog.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
          unrelatedAuditLog.setEntityKey(decisionInstance.getId());
          unrelatedAuditLog.setEntityType(AuditLogEntityType.BATCH); // different entity type

          store(decisionInstanceTemplate, client, decisionInstance);
          store(auditLogTemplate, client, unrelatedAuditLog);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(decisionInstanceTemplate, client, decisionInstance, "2020-01-01");
          verifyNotMoved(auditLogTemplate, client, unrelatedAuditLog);
        });
  }

  private DecisionInstanceEntity decisionInstance(final String evaluationDate) {
    final var entity = new DecisionInstanceEntity();
    entity.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
    entity.setKey(Long.parseLong(entity.getId()));
    entity.setPartitionId(PARTITION_ID);
    entity.setEvaluationDate(OffsetDateTime.parse(evaluationDate));
    entity.setState(DecisionInstanceState.EVALUATED);
    entity.setProcessInstanceKey(-1); // -1 indicates standalone
    entity.setDecisionId("test-decision");
    entity.setDecisionName("Test Decision");
    entity.setDecisionVersion(1);
    entity.setDecisionType(io.camunda.webapps.schema.entities.dmn.DecisionType.DECISION_TABLE);
    return entity;
  }
}
