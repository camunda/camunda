/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunEnrichmentService;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunRecordFolder.FoldResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AgentRunDatabaseImportJobTest {

  private final AgentRunWriter agentRunWriter = mock(AgentRunWriter.class);
  private final AgentRunEnrichmentService enrichmentService = mock(AgentRunEnrichmentService.class);
  private final DatabaseClient databaseClient = mock(DatabaseClient.class);
  private final AgentRunDatabaseImportJob underTest =
      new AgentRunDatabaseImportJob(
          agentRunWriter,
          enrichmentService,
          mock(ConfigurationService.class),
          () -> {},
          databaseClient,
          1);

  @Test
  void shouldEnrichRunsCompletedInThePageAfterWritingThem() {
    // given one run whose COMPLETED record is in the page and one still running
    final AgentRunDto completed = run("700", OffsetDateTime.now());
    final AgentRunDto running = run("701", null);

    // when
    underTest.persistEntities(
        List.of(new FoldResult(completed, List.of()), new FoldResult(running, List.of())));

    // then
    final InOrder order = inOrder(databaseClient, enrichmentService);
    order
        .verify(databaseClient)
        .executeImportRequestsAsBulk(eq("Zeebe agent runs"), any(), anyBoolean());
    order.verify(enrichmentService).enrichCompletedRuns(List.of("700"));
    // agent configuration is read from the runs, so the definition doc is not written per page
    verify(databaseClient, never())
        .executeImportRequestsAsBulk(eq("Zeebe agent definition rollups"), any(), anyBoolean());
    verify(enrichmentService).addAgenticProcesses(List.of("loan", "loan"));
  }

  private static AgentRunDto run(final String id, final OffsetDateTime endDate) {
    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId(id);
    run.setProcessDefinitionKey("loan");
    run.setEndDate(endDate);
    return run;
  }
}
