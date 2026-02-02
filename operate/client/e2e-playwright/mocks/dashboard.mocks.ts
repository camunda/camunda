/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Route} from '@playwright/test';
import type {GetProcessDefinitionInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import type {
  GetIncidentProcessInstanceStatisticsByErrorResponseBody,
  GetIncidentProcessInstanceStatisticsByDefinitionResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';

const mockIncidentsByDefinition: GetIncidentProcessInstanceStatisticsByDefinitionResponseBody =
  {
    items: [
      {
        processDefinitionId: 'orderProcess',
        processDefinitionKey: 2251799813687188,
        processDefinitionName: 'Order process',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 131,
      },
      {
        processDefinitionId: 'orderProcess',
        processDefinitionKey: 2251799813686114,
        processDefinitionName: 'Order process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 10,
      },
      {
        processDefinitionId: 'call-activity-process',
        processDefinitionKey: 2251799813686145,
        processDefinitionName: 'Call Activity Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 90,
      },
      {
        processDefinitionId: 'complexProcess',
        processDefinitionKey: 2251799813687889,
        processDefinitionVersion: 3,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 56,
      },
      {
        processDefinitionId: 'complexProcess',
        processDefinitionKey: 2251799813687201,
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 20,
      },
      {
        processDefinitionId: 'complexProcess',
        processDefinitionKey: 2251799813686132,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 14,
      },
      {
        processDefinitionId: 'called-process',
        processDefinitionKey: 2251799813687891,
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 56,
      },
      {
        processDefinitionId: 'called-process',
        processDefinitionKey: 2251799813687210,
        processDefinitionName: 'Called Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 14,
      },
      {
        processDefinitionId: 'invoice',
        processDefinitionKey: 2251799813686130,
        processDefinitionName: 'DMN invoice',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 33,
      },
      {
        processDefinitionId: 'eventBasedGatewayProcess',
        processDefinitionKey: 2251799813687203,
        processDefinitionName: 'Event based gateway with timer start',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 20,
      },
      {
        processDefinitionId: 'eventBasedGatewayProcess',
        processDefinitionKey: 2251799813686134,
        processDefinitionName: 'Event based gateway with message start',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 6,
      },
      {
        processDefinitionId: 'flightRegistration',
        processDefinitionKey: 2251799813687190,
        processDefinitionName: 'Flight registration',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 13,
      },
      {
        processDefinitionId: 'flightRegistration',
        processDefinitionKey: 2251799813686118,
        processDefinitionName: 'Flight registration',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 10,
      },
      {
        processDefinitionId: 'error-end-process',
        processDefinitionKey: 2251799813686153,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 20,
      },
      {
        processDefinitionId: 'nonInterruptingBoundaryEvent',
        processDefinitionKey: 2251799813687208,
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 16,
      },
      {
        processDefinitionId: 'nonInterruptingBoundaryEvent',
        processDefinitionKey: 2251799813686141,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 3,
      },
      {
        processDefinitionId: 'prWithSubprocess',
        processDefinitionKey: 2251799813686137,
        processDefinitionName: 'Nested subprocesses',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 19,
      },
      {
        processDefinitionId: 'multiInstanceProcess',
        processDefinitionKey: 2251799813687192,
        processDefinitionName: 'Multi-Instance Process',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 17,
      },
      {
        processDefinitionId: 'multiInstanceProcess',
        processDefinitionKey: 2251799813686120,
        processDefinitionName: 'Sequential Multi-Instance Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'errorProcess',
        processDefinitionKey: 2251799813686151,
        processDefinitionName: 'Error Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 17,
      },
      {
        processDefinitionId: 'loanProcess',
        processDefinitionKey: 2251799813686116,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 15,
      },
      {
        processDefinitionId: 'eventSubprocessProcess',
        processDefinitionKey: 2251799813686147,
        processDefinitionName: 'Event Subprocess Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 9,
      },
      {
        processDefinitionId: 'interruptingBoundaryEvent',
        processDefinitionKey: 2251799813687206,
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 8,
      },
      {
        processDefinitionId: 'interruptingBoundaryEvent',
        processDefinitionKey: 2251799813686139,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 1,
      },
      {
        processDefinitionId: 'inclusiveGatewayProcess',
        processDefinitionKey: 2251799813686168,
        processDefinitionName: 'Inclusive gateway',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 7,
      },
      {
        processDefinitionId: 'linkEventProcess',
        processDefinitionKey: 2251799813686161,
        processDefinitionName: 'Link events process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 6,
      },
      {
        processDefinitionId: 'onlyIncidentsProcess',
        processDefinitionKey: 2251799813685257,
        processDefinitionName: 'Only Incidents Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 1,
      },
      {
        processDefinitionId: 'onlyIncidentsProcess',
        processDefinitionKey: 2251799813685301,
        processDefinitionName: 'Only Incidents Process',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 1,
      },
      {
        processDefinitionId: 'bigProcess',
        processDefinitionKey: 2251799813686149,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 1,
      },
      {
        processDefinitionId: 'escalationEvents',
        processDefinitionKey: 2251799813687212,
        processDefinitionName: 'Escalation events',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'escalationEvents',
        processDefinitionKey: 2251799813686163,
        processDefinitionName: 'Escalation events',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'withoutIncidentsProcess',
        processDefinitionKey: 2251799813685364,
        processDefinitionName: 'Without Incidents Process',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'withoutIncidentsProcess',
        processDefinitionKey: 2251799813685350,
        processDefinitionName: 'Without Incidents Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'signalEventProcess',
        processDefinitionKey: 2251799813686165,
        processDefinitionName: 'Signal event',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'intermediate-message-throw-event-process',
        processDefinitionKey: 2251799813686124,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'terminateEndEvent',
        processDefinitionKey: 2251799813686155,
        processDefinitionName: 'Terminate End Event',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'dataStoreProcess',
        processDefinitionKey: 2251799813686159,
        processDefinitionName: 'Data store process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'message-end-event-process',
        processDefinitionKey: 2251799813686128,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'timerProcess',
        processDefinitionKey: 2251799813687198,
        processDefinitionName: 'Timer process',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'timerProcess',
        processDefinitionKey: 2251799813686143,
        processDefinitionName: 'Timer process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'bigVarProcess',
        processDefinitionKey: 2251799813685430,
        processDefinitionName: 'Big variable process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f',
        processDefinitionKey: 2251799813685251,
        processDefinitionName: 'Input Output Mapping Test',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'always-completing-process',
        processDefinitionKey: 2251799813685249,
        processDefinitionName: 'Always completing process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'intermediate-none-event-process',
        processDefinitionKey: 2251799813686126,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'manual-task-process',
        processDefinitionKey: 2251799813686122,
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'noInstancesProcess',
        processDefinitionKey: 2251799813685253,
        processDefinitionName: 'Without Instances Process',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'noInstancesProcess',
        processDefinitionKey: 2251799813685255,
        processDefinitionName: 'Without Instances Process',
        processDefinitionVersion: 2,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
      {
        processDefinitionId: 'undefined-task-process',
        processDefinitionKey: 2251799813686157,
        processDefinitionName: 'undefined-task',
        processDefinitionVersion: 1,
        tenantId: '<default>',
        activeInstancesWithErrorCount: 0,
      },
    ],
    page: {totalItems: 48},
  };

const mockIncidentsByError: GetIncidentProcessInstanceStatisticsByErrorResponseBody =
  {
    items: [
      {
        errorHashCode: 1111,
        errorMessage: 'No more retries left.',
        activeInstancesWithErrorCount: 170,
      },
      {
        errorHashCode: 1112,
        errorMessage:
          "failed to evaluate expression '{orderId:orderNo,amountToPay:total}': no variable found for name 'total'",
        activeInstancesWithErrorCount: 112,
      },
      {
        errorHashCode: 1113,
        errorMessage:
          "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
        activeInstancesWithErrorCount: 76,
      },
      {
        errorHashCode: 1114,
        errorMessage:
          'Expected at least one condition to evaluate to true, or to have a default flow',
        activeInstancesWithErrorCount: 63,
      },
      {
        errorHashCode: 1115,
        errorMessage:
          "Expected result of the expression ' list contains(flows,\"2\")' to be 'BOOLEAN', but was 'NULL'.",
        activeInstancesWithErrorCount: 56,
      },
      {
        errorHashCode: 1116,
        errorMessage:
          "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'",
        activeInstancesWithErrorCount: 56,
      },
      {
        errorHashCode: 1117,
        errorMessage:
          'Something went wrong. \njava.lang.Throwable\n\tat io.camunda.operate.data.usertest.UserTestDataGenerator.lambda$progressAlwaysFailingTask$3(UserTestDataGenerator.java:359)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:577)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)\n\tat java.base/java.lang.Thread.run(Thread.java:1589)',
        activeInstancesWithErrorCount: 55,
      },
      {
        errorHashCode: 1118,
        errorMessage:
          "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
        activeInstancesWithErrorCount: 20,
      },
      {
        errorHashCode: 1119,
        errorMessage:
          "Expected to throw an error event with the code 'end', but it was not caught. No error events are available in the scope.",
        activeInstancesWithErrorCount: 20,
      },
      {
        errorHashCode: 1120,
        errorMessage:
          "Expected to evaluate decision 'invoiceAssignApprover', but failed to evaluate expression 'amount': no variable found for name 'amount'",
        activeInstancesWithErrorCount: 17,
      },
      {
        errorHashCode: 1121,
        errorMessage:
          "Expected to throw an error event with the code 'unknown' with message 'Job worker throw error with error code: unknown', but it was not caught. Available error events are [boundary, subProcess]",
        activeInstancesWithErrorCount: 17,
      },
      {
        errorHashCode: 1122,
        errorMessage:
          "Expected to evaluate decision 'invoiceAssignApprover', but no decision found for id 'invoiceAssignApprover'",
        activeInstancesWithErrorCount: 16,
      },
      {
        errorHashCode: 1123,
        errorMessage: 'Loan request does not contain all the required data',
        activeInstancesWithErrorCount: 10,
      },
      {
        errorHashCode: 1124,
        errorMessage: 'Cannot connect to server delivery05',
        activeInstancesWithErrorCount: 9,
      },
      {
        errorHashCode: 1125,
        errorMessage:
          "failed to evaluate expression 'paid = false': no variable found for name 'paid'",
        activeInstancesWithErrorCount: 4,
      },
      {
        errorHashCode: 1126,
        errorMessage:
          "failed to evaluate expression 'paid = true': no variable found for name 'paid'",
        activeInstancesWithErrorCount: 4,
      },
      {
        errorHashCode: 1127,
        errorMessage: 'Schufa system is not accessible',
        activeInstancesWithErrorCount: 2,
      },
      {
        errorHashCode: 1128,
        errorMessage: 'No memory left.',
        activeInstancesWithErrorCount: 1,
      },
      {
        errorHashCode: 1129,
        errorMessage: 'No space left on device.',
        activeInstancesWithErrorCount: 1,
      },
      {
        errorHashCode: 1130,
        errorMessage: 'error',
        activeInstancesWithErrorCount: 1,
      },
      {
        errorHashCode: 1131,
        errorMessage:
          "failed to evaluate expression 'smthIsMissing = true': no variable found for name 'smthIsMissing'",
        activeInstancesWithErrorCount: 1,
      },
      {
        errorHashCode: 1132,
        errorMessage:
          'java.lang.RuntimeException: Payment system not available.\n\tat io.camunda.operate.data.develop.DevelopDataGenerator.lambda$progressOrderProcessCheckPayment$0(DevelopDataGenerator.java:233)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:577)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)\n\tat java.base/java.lang.Thread.run(Thread.java:1589)',
        activeInstancesWithErrorCount: 1,
      },
    ],
    page: {totalItems: 22},
  };

const mockProcessDefinitionStatistics: GetProcessDefinitionInstanceStatisticsResponseBody =
  {
    items: [
      {
        processDefinitionId: 'orderProcess',
        latestProcessDefinitionName: 'Order process',
        activeInstancesWithIncidentCount: 141,
        activeInstancesWithoutIncidentCount: 5,
        hasMultipleVersions: true,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'call-activity-process',
        latestProcessDefinitionName: 'Call Activity Process',
        activeInstancesWithIncidentCount: 90,
        activeInstancesWithoutIncidentCount: 15,
        hasMultipleVersions: false,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'complexProcess',
        latestProcessDefinitionName: '',
        activeInstancesWithIncidentCount: 90,
        activeInstancesWithoutIncidentCount: 13,
        hasMultipleVersions: true,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'called-process',
        latestProcessDefinitionName: '',
        activeInstancesWithIncidentCount: 70,
        activeInstancesWithoutIncidentCount: 0,
        hasMultipleVersions: true,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'invoice',
        latestProcessDefinitionName: 'DMN invoice',
        activeInstancesWithIncidentCount: 33,
        activeInstancesWithoutIncidentCount: 35,
        hasMultipleVersions: false,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'eventBasedGatewayProcess',
        latestProcessDefinitionName: 'Event based gateway with timer start',
        activeInstancesWithIncidentCount: 26,
        activeInstancesWithoutIncidentCount: 0,
        hasMultipleVersions: true,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'flightRegistration',
        latestProcessDefinitionName: 'Flight registration',
        activeInstancesWithIncidentCount: 23,
        activeInstancesWithoutIncidentCount: 4,
        hasMultipleVersions: true,
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 7},
  };

const mockProcessDefinitionVersionStatistics = {
  orderProcess: {
    items: [
      {
        processDefinitionId: '2251799813687188',
        processDefinitionKey: '2251799813687188',
        processDefinitionName: 'Order process',
        processDefinitionVersion: 2,
        activeInstancesWithIncidentCount: 131,
        activeInstancesWithoutIncidentCount: 5,
        tenantId: '<default>',
      },
      {
        processDefinitionId: '2251799813686114',
        processDefinitionKey: '2251799813686114',
        processDefinitionName: 'Order process',
        processDefinitionVersion: 1,
        activeInstancesWithIncidentCount: 10,
        activeInstancesWithoutIncidentCount: 0,
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 2},
  },
  complexProcess: {
    items: [
      {
        processDefinitionId: '2251799813687889',
        processDefinitionKey: '2251799813687889',
        processDefinitionName: null,
        processDefinitionVersion: 3,
        activeInstancesWithIncidentCount: 56,
        activeInstancesWithoutIncidentCount: 0,
        tenantId: '<default>',
      },
      {
        processDefinitionId: '2251799813687201',
        processDefinitionKey: '2251799813687201',
        processDefinitionName: 'complexProcess',
        processDefinitionVersion: 2,
        activeInstancesWithIncidentCount: 20,
        activeInstancesWithoutIncidentCount: 9,
        tenantId: '<default>',
      },
      {
        processDefinitionId: '2251799813686132',
        processDefinitionKey: '2251799813686132',
        processDefinitionName: 'complexProcess',
        processDefinitionVersion: 1,
        activeInstancesWithIncidentCount: 14,
        activeInstancesWithoutIncidentCount: 4,
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 3},
  },
};

function mockResponses({
  incidentsByError,
  incidentsByDefinition,
  processDefinitionStatistics,
}: {
  incidentsByError?: GetIncidentProcessInstanceStatisticsByErrorResponseBody;
  incidentsByDefinition?: GetIncidentProcessInstanceStatisticsByDefinitionResponseBody;
  processDefinitionStatistics?: GetProcessDefinitionInstanceStatisticsResponseBody;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          displayName: 'demo',
          canLogout: true,
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route
        .request()
        .url()
        .includes('/v2/incidents/statistics/process-instances-by-error')
    ) {
      return route.fulfill({
        status: incidentsByError === undefined ? 400 : 200,
        body: JSON.stringify(incidentsByError),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route
        .request()
        .url()
        .includes('/v2/incidents/statistics/process-instances-by-definition')
    ) {
      return route.fulfill({
        status: incidentsByDefinition === undefined ? 400 : 200,
        body: JSON.stringify(incidentsByDefinition),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    const url = route.request().url();
    const method = route.request().method();

    if (
      url.includes(
        '/v2/process-definitions/orderProcess/statistics/process-instances',
      ) &&
      method === 'POST'
    ) {
      route.fulfill({
        status: 200,
        body: JSON.stringify(
          mockProcessDefinitionVersionStatistics.orderProcess,
        ),
        headers: {
          'content-type': 'application/json',
        },
      });
      return;
    }

    if (
      route
        .request()
        .url()
        .includes('/v2/process-definitions/statistics/process-instances')
    ) {
      return route.fulfill({
        status: processDefinitionStatistics === undefined ? 400 : 200,
        body: JSON.stringify(processDefinitionStatistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

export {
  mockIncidentsByError,
  mockIncidentsByDefinition,
  mockProcessDefinitionStatistics,
  mockProcessDefinitionVersionStatistics,
  mockResponses,
};
