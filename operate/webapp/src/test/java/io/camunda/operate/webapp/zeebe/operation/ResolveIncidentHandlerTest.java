/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientFutureImpl;
import io.camunda.client.impl.command.JobUpdateRetriesCommandImpl;
import io.camunda.client.impl.command.ResolveIncidentCommandImpl;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.webapp.elasticsearch.writer.BatchOperationWriter;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ResolveIncidentHandlerTest {

  @Mock IncidentReader incidentReader;
  @Spy CamundaClient camundaClient;
  @Spy OperateServicesAdapter operateServicesAdapter;
  @Mock JobUpdateRetriesCommandImpl updateRetriesCommand;
  @Mock ResolveIncidentCommandImpl resolveIncidentCommand;

  @Mock BatchOperationWriter batchOperationWriter;

  @Mock Metrics metrics;

  @Mock OperateProperties operateProperties;

  @InjectMocks private ResolveIncidentHandler resolveIncidentHandler;

  @Test
  public void shouldUpdateRetriesBeforeResolvingTaskListenerIncident() throws Exception {
    // given
    final Long incidentKey = 123L;
    final Long jobKey = 1L;
    final String workerId = "1";
    when(incidentReader.getIncidentById(incidentKey))
        .thenReturn(
            new IncidentEntity()
                .setErrorType(ErrorType.TASK_LISTENER_NO_RETRIES)
                .setJobKey(jobKey)
                .setKey(incidentKey));

    final String operationId = "456";
    final OperationEntity operationEntity =
        new OperationEntity()
            .setType(OperationType.RESOLVE_INCIDENT)
            .setIncidentKey(incidentKey)
            .setId(operationId)
            .setState(OperationState.LOCKED)
            .setLockOwner(workerId);

    final CamundaClientFutureImpl future1 = new CamundaClientFutureImpl<>();
    final CamundaClientFutureImpl future2 = new CamundaClientFutureImpl<>();
    future1.complete(null);
    future2.complete(null);

    doNothing().when(batchOperationWriter).updateOperation(any());
    doNothing().when(metrics).recordCounts(any(String.class), any(long.class), any(String[].class));
    final OperationExecutorProperties properties = new OperationExecutorProperties();
    properties.setWorkerId(workerId);
    when(operateProperties.getOperationExecutor()).thenReturn(properties);

    final InOrder inOrder = Mockito.inOrder(operateServicesAdapter);

    // when
    resolveIncidentHandler.handleWithException(operationEntity);

    // then
    verify(operateServicesAdapter, times(1)).updateJobRetries(jobKey, 1, operationId);
    verify(operateServicesAdapter, times(1)).resolveIncident(incidentKey, operationId);
    inOrder.verify(operateServicesAdapter).updateJobRetries(jobKey, 1, operationId);
    inOrder.verify(operateServicesAdapter).resolveIncident(incidentKey, operationId);
  }
}
