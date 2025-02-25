/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route} from '@playwright/test';
import {IncidentByErrorDto} from 'modules/api/incidents/fetchIncidentsByError';
import {ProcessInstanceByNameDto} from 'modules/api/incidents/fetchProcessInstancesByName';
import {CoreStatisticsDto} from 'modules/api/processInstances/fetchProcessCoreStatistics';

const mockStatistics = {
  running: 891,
  active: 277,
  withIncidents: 614,
};

const mockIncidentsByError = [
  {
    errorMessage: 'No more retries left.',
    instancesWithErrorCount: 170,
    processes: [
      {
        processId: '2251799813687201',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686137',
        tenantId: '<default>',
        version: 1,
        name: 'Nested subprocesses',
        bpmnProcessId: 'prWithSubprocess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 19,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687192',
        tenantId: '<default>',
        version: 2,
        name: 'Multi-Instance Process',
        bpmnProcessId: 'multiInstanceProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 17,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687208',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 16,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687210',
        tenantId: '<default>',
        version: 1,
        name: 'Called Process',
        bpmnProcessId: 'called-process',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 14,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686132',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 14,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687190',
        tenantId: '<default>',
        version: 2,
        name: 'Flight registration',
        bpmnProcessId: 'flightRegistration',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 13,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686118',
        tenantId: '<default>',
        version: 1,
        name: 'Flight registration',
        bpmnProcessId: 'flightRegistration',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 10,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686147',
        tenantId: '<default>',
        version: 1,
        name: 'Event Subprocess Process',
        bpmnProcessId: 'eventSubprocessProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 9,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687206',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'interruptingBoundaryEvent',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 8,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687188',
        tenantId: '<default>',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 8,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686134',
        tenantId: '<default>',
        version: 1,
        name: 'Event based gateway with message start',
        bpmnProcessId: 'eventBasedGatewayProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 6,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686161',
        tenantId: '<default>',
        version: 1,
        name: 'Link events process',
        bpmnProcessId: 'linkEventProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 6,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686116',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'loanProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 3,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686141',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 3,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686114',
        tenantId: '<default>',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 2,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686149',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'bigProcess',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686139',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'interruptingBoundaryEvent',
        errorMessage: 'No more retries left.',
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "failed to evaluate expression '{orderId:orderNo,amountToPay:total}': no variable found for name 'total'",
    instancesWithErrorCount: 112,
    processes: [
      {
        processId: '2251799813687188',
        tenantId: '<default>',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage:
          "failed to evaluate expression '{orderId:orderNo,amountToPay:total}': no variable found for name 'total'",
        instancesWithActiveIncidentsCount: 112,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
    instancesWithErrorCount: 76,
    processes: [
      {
        processId: '2251799813687889',
        tenantId: '<default>',
        version: 3,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage:
          "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
        instancesWithActiveIncidentsCount: 56,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687203',
        tenantId: '<default>',
        version: 2,
        name: 'Event based gateway with timer start',
        bpmnProcessId: 'eventBasedGatewayProcess',
        errorMessage:
          "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      'Expected at least one condition to evaluate to true, or to have a default flow',
    instancesWithErrorCount: 63,
    processes: [
      {
        processId: '2251799813687889',
        tenantId: '<default>',
        version: 3,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage:
          'Expected at least one condition to evaluate to true, or to have a default flow',
        instancesWithActiveIncidentsCount: 56,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686168',
        tenantId: '<default>',
        version: 1,
        name: 'Inclusive gateway',
        bpmnProcessId: 'inclusiveGatewayProcess',
        errorMessage:
          'Expected at least one condition to evaluate to true, or to have a default flow',
        instancesWithActiveIncidentsCount: 7,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "Expected result of the expression ' list contains(flows,\"2\")' to be 'BOOLEAN', but was 'NULL'.",
    instancesWithErrorCount: 56,
    processes: [
      {
        processId: '2251799813687889',
        tenantId: '<default>',
        version: 3,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage:
          "Expected result of the expression ' list contains(flows,\"2\")' to be 'BOOLEAN', but was 'NULL'.",
        instancesWithActiveIncidentsCount: 56,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'",
    instancesWithErrorCount: 56,
    processes: [
      {
        processId: '2251799813687889',
        tenantId: '<default>',
        version: 3,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage:
          "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'",
        instancesWithActiveIncidentsCount: 56,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      'Something went wrong. \njava.lang.Throwable\n\tat io.camunda.operate.data.usertest.UserTestDataGenerator.lambda$progressAlwaysFailingTask$3(UserTestDataGenerator.java:359)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:577)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)\n\tat java.base/java.lang.Thread.run(Thread.java:1589)',
    instancesWithErrorCount: 55,
    processes: [
      {
        processId: '2251799813687889',
        tenantId: '<default>',
        version: 3,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage:
          'Something went wrong. \njava.lang.Throwable\n\tat io.camunda.operate.data.usertest.UserTestDataGenerator.lambda$progressAlwaysFailingTask$3(UserTestDataGenerator.java:359)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:577)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)\n\tat java.base/java.lang.Thread.run(Thread.java:1589)',
        instancesWithActiveIncidentsCount: 55,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
    instancesWithErrorCount: 20,
    processes: [
      {
        processId: '2251799813686145',
        tenantId: '<default>',
        version: 1,
        name: 'Call Activity Process',
        bpmnProcessId: 'call-activity-process',
        errorMessage:
          "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "Expected to throw an error event with the code 'end', but it was not caught. No error events are available in the scope.",
    instancesWithErrorCount: 20,
    processes: [
      {
        processId: '2251799813686153',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'error-end-process',
        errorMessage:
          "Expected to throw an error event with the code 'end', but it was not caught. No error events are available in the scope.",
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "Expected to evaluate decision 'invoiceAssignApprover', but failed to evaluate expression 'amount': no variable found for name 'amount'",
    instancesWithErrorCount: 17,
    processes: [
      {
        processId: '2251799813686130',
        tenantId: '<default>',
        version: 1,
        name: 'DMN invoice',
        bpmnProcessId: 'invoice',
        errorMessage:
          "Expected to evaluate decision 'invoiceAssignApprover', but failed to evaluate expression 'amount': no variable found for name 'amount'",
        instancesWithActiveIncidentsCount: 17,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "Expected to throw an error event with the code 'unknown' with message 'Job worker throw error with error code: unknown', but it was not caught. Available error events are [boundary, subProcess]",
    instancesWithErrorCount: 17,
    processes: [
      {
        processId: '2251799813686151',
        tenantId: '<default>',
        version: 1,
        name: 'Error Process',
        bpmnProcessId: 'errorProcess',
        errorMessage:
          "Expected to throw an error event with the code 'unknown' with message 'Job worker throw error with error code: unknown', but it was not caught. Available error events are [boundary, subProcess]",
        instancesWithActiveIncidentsCount: 17,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "Expected to evaluate decision 'invoiceAssignApprover', but no decision found for id 'invoiceAssignApprover'",
    instancesWithErrorCount: 16,
    processes: [
      {
        processId: '2251799813686130',
        tenantId: '<default>',
        version: 1,
        name: 'DMN invoice',
        bpmnProcessId: 'invoice',
        errorMessage:
          "Expected to evaluate decision 'invoiceAssignApprover', but no decision found for id 'invoiceAssignApprover'",
        instancesWithActiveIncidentsCount: 16,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage: 'Loan request does not contain all the required data',
    instancesWithErrorCount: 10,
    processes: [
      {
        processId: '2251799813686116',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'loanProcess',
        errorMessage: 'Loan request does not contain all the required data',
        instancesWithActiveIncidentsCount: 10,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage: 'Cannot connect to server delivery05',
    instancesWithErrorCount: 9,
    processes: [
      {
        processId: '2251799813687188',
        tenantId: '<default>',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: 'Cannot connect to server delivery05',
        instancesWithActiveIncidentsCount: 6,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686114',
        tenantId: '<default>',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: 'Cannot connect to server delivery05',
        instancesWithActiveIncidentsCount: 3,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "failed to evaluate expression 'paid = false': no variable found for name 'paid'",
    instancesWithErrorCount: 4,
    processes: [
      {
        processId: '2251799813687188',
        tenantId: '<default>',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage:
          "failed to evaluate expression 'paid = false': no variable found for name 'paid'",
        instancesWithActiveIncidentsCount: 4,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "failed to evaluate expression 'paid = true': no variable found for name 'paid'",
    instancesWithErrorCount: 4,
    processes: [
      {
        processId: '2251799813686114',
        tenantId: '<default>',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage:
          "failed to evaluate expression 'paid = true': no variable found for name 'paid'",
        instancesWithActiveIncidentsCount: 4,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage: 'Schufa system is not accessible',
    instancesWithErrorCount: 2,
    processes: [
      {
        processId: '2251799813686116',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'loanProcess',
        errorMessage: 'Schufa system is not accessible',
        instancesWithActiveIncidentsCount: 2,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage: 'No memory left.',
    instancesWithErrorCount: 1,
    processes: [
      {
        processId: '2251799813685257',
        tenantId: '<default>',
        version: 1,
        name: 'Only Incidents Process',
        bpmnProcessId: 'onlyIncidentsProcess',
        errorMessage: 'No memory left.',
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage: 'No space left on device.',
    instancesWithErrorCount: 1,
    processes: [
      {
        processId: '2251799813685301',
        tenantId: '<default>',
        version: 2,
        name: 'Only Incidents Process',
        bpmnProcessId: 'onlyIncidentsProcess',
        errorMessage: 'No space left on device.',
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage: 'error',
    instancesWithErrorCount: 1,
    processes: [
      {
        processId: '2251799813686141',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
        errorMessage: 'error',
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      "failed to evaluate expression 'smthIsMissing = true': no variable found for name 'smthIsMissing'",
    instancesWithErrorCount: 1,
    processes: [
      {
        processId: '2251799813687188',
        tenantId: '<default>',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage:
          "failed to evaluate expression 'smthIsMissing = true': no variable found for name 'smthIsMissing'",
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    errorMessage:
      'java.lang.RuntimeException: Payment system not available.\n\tat io.camunda.operate.data.develop.DevelopDataGenerator.lambda$progressOrderProcessCheckPayment$0(DevelopDataGenerator.java:233)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:577)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)\n\tat java.base/java.lang.Thread.run(Thread.java:1589)',
    instancesWithErrorCount: 1,
    processes: [
      {
        processId: '2251799813686114',
        tenantId: '<default>',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage:
          'java.lang.RuntimeException: Payment system not available.\n\tat io.camunda.operate.data.develop.DevelopDataGenerator.lambda$progressOrderProcessCheckPayment$0(DevelopDataGenerator.java:233)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:577)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)\n\tat java.base/java.lang.Thread.run(Thread.java:1589)',
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
];

const mockIncidentsByProcess = [
  {
    bpmnProcessId: 'orderProcess',
    tenantId: '<default>',
    processName: 'Order process',
    instancesWithActiveIncidentsCount: 141,
    activeInstancesCount: 5,
    processes: [
      {
        processId: '2251799813687188',
        tenantId: '<default>',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 131,
        activeInstancesCount: 5,
      },
      {
        processId: '2251799813686114',
        tenantId: '<default>',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 10,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'call-activity-process',
    tenantId: '<default>',
    processName: 'Call Activity Process',
    instancesWithActiveIncidentsCount: 90,
    activeInstancesCount: 15,
    processes: [
      {
        processId: '2251799813686145',
        tenantId: '<default>',
        version: 1,
        name: 'Call Activity Process',
        bpmnProcessId: 'call-activity-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 90,
        activeInstancesCount: 15,
      },
    ],
  },
  {
    bpmnProcessId: 'complexProcess',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 90,
    activeInstancesCount: 13,
    processes: [
      {
        processId: '2251799813687889',
        tenantId: '<default>',
        version: 3,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 56,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687201',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 9,
      },
      {
        processId: '2251799813686132',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'complexProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 14,
        activeInstancesCount: 4,
      },
    ],
  },
  {
    bpmnProcessId: 'called-process',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 70,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813687891',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'called-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 56,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813687210',
        tenantId: '<default>',
        version: 1,
        name: 'Called Process',
        bpmnProcessId: 'called-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 14,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'invoice',
    tenantId: '<default>',
    processName: 'DMN invoice',
    instancesWithActiveIncidentsCount: 33,
    activeInstancesCount: 35,
    processes: [
      {
        processId: '2251799813686130',
        tenantId: '<default>',
        version: 1,
        name: 'DMN invoice',
        bpmnProcessId: 'invoice',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 33,
        activeInstancesCount: 35,
      },
    ],
  },
  {
    bpmnProcessId: 'eventBasedGatewayProcess',
    tenantId: '<default>',
    processName: 'Event based gateway with timer start',
    instancesWithActiveIncidentsCount: 26,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813687203',
        tenantId: '<default>',
        version: 2,
        name: 'Event based gateway with timer start',
        bpmnProcessId: 'eventBasedGatewayProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686134',
        tenantId: '<default>',
        version: 1,
        name: 'Event based gateway with message start',
        bpmnProcessId: 'eventBasedGatewayProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 6,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'flightRegistration',
    tenantId: '<default>',
    processName: 'Flight registration',
    instancesWithActiveIncidentsCount: 23,
    activeInstancesCount: 4,
    processes: [
      {
        processId: '2251799813687190',
        tenantId: '<default>',
        version: 2,
        name: 'Flight registration',
        bpmnProcessId: 'flightRegistration',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 13,
        activeInstancesCount: 4,
      },
      {
        processId: '2251799813686118',
        tenantId: '<default>',
        version: 1,
        name: 'Flight registration',
        bpmnProcessId: 'flightRegistration',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 10,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'error-end-process',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 20,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686153',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'error-end-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 20,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'nonInterruptingBoundaryEvent',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 19,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813687208',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 16,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686141',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 3,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'prWithSubprocess',
    tenantId: '<default>',
    processName: 'Nested subprocesses',
    instancesWithActiveIncidentsCount: 19,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686137',
        tenantId: '<default>',
        version: 1,
        name: 'Nested subprocesses',
        bpmnProcessId: 'prWithSubprocess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 19,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'multiInstanceProcess',
    tenantId: '<default>',
    processName: 'Multi-Instance Process',
    instancesWithActiveIncidentsCount: 17,
    activeInstancesCount: 15,
    processes: [
      {
        processId: '2251799813687192',
        tenantId: '<default>',
        version: 2,
        name: 'Multi-Instance Process',
        bpmnProcessId: 'multiInstanceProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 17,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686120',
        tenantId: '<default>',
        version: 1,
        name: 'Sequential Multi-Instance Process',
        bpmnProcessId: 'multiInstanceProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 15,
      },
    ],
  },
  {
    bpmnProcessId: 'errorProcess',
    tenantId: '<default>',
    processName: 'Error Process',
    instancesWithActiveIncidentsCount: 17,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686151',
        tenantId: '<default>',
        version: 1,
        name: 'Error Process',
        bpmnProcessId: 'errorProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 17,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'loanProcess',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 15,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686116',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'loanProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 15,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'eventSubprocessProcess',
    tenantId: '<default>',
    processName: 'Event Subprocess Process',
    instancesWithActiveIncidentsCount: 9,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686147',
        tenantId: '<default>',
        version: 1,
        name: 'Event Subprocess Process',
        bpmnProcessId: 'eventSubprocessProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 9,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'interruptingBoundaryEvent',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 9,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813687206',
        tenantId: '<default>',
        version: 2,
        name: null,
        bpmnProcessId: 'interruptingBoundaryEvent',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 8,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813686139',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'interruptingBoundaryEvent',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'inclusiveGatewayProcess',
    tenantId: '<default>',
    processName: 'Inclusive gateway',
    instancesWithActiveIncidentsCount: 7,
    activeInstancesCount: 12,
    processes: [
      {
        processId: '2251799813686168',
        tenantId: '<default>',
        version: 1,
        name: 'Inclusive gateway',
        bpmnProcessId: 'inclusiveGatewayProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 7,
        activeInstancesCount: 12,
      },
    ],
  },
  {
    bpmnProcessId: 'linkEventProcess',
    tenantId: '<default>',
    processName: 'Link events process',
    instancesWithActiveIncidentsCount: 6,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686161',
        tenantId: '<default>',
        version: 1,
        name: 'Link events process',
        bpmnProcessId: 'linkEventProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 6,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'onlyIncidentsProcess',
    tenantId: '<default>',
    processName: 'Only Incidents Process',
    instancesWithActiveIncidentsCount: 2,
    activeInstancesCount: 20,
    processes: [
      {
        processId: '2251799813685257',
        tenantId: '<default>',
        version: 1,
        name: 'Only Incidents Process',
        bpmnProcessId: 'onlyIncidentsProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 10,
      },
      {
        processId: '2251799813685301',
        tenantId: '<default>',
        version: 2,
        name: 'Only Incidents Process',
        bpmnProcessId: 'onlyIncidentsProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 10,
      },
    ],
  },
  {
    bpmnProcessId: 'bigProcess',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 1,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686149',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'bigProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 1,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'escalationEvents',
    tenantId: '<default>',
    processName: 'Escalation events',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 46,
    processes: [
      {
        processId: '2251799813687212',
        tenantId: '<default>',
        version: 2,
        name: 'Escalation events',
        bpmnProcessId: 'escalationEvents',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 29,
      },
      {
        processId: '2251799813686163',
        tenantId: '<default>',
        version: 1,
        name: 'Escalation events',
        bpmnProcessId: 'escalationEvents',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 17,
      },
    ],
  },
  {
    bpmnProcessId: 'withoutIncidentsProcess',
    tenantId: '<default>',
    processName: 'Without Incidents Process',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 26,
    processes: [
      {
        processId: '2251799813685364',
        tenantId: '<default>',
        version: 2,
        name: 'Without Incidents Process',
        bpmnProcessId: 'withoutIncidentsProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 17,
      },
      {
        processId: '2251799813685350',
        tenantId: '<default>',
        version: 1,
        name: 'Without Incidents Process',
        bpmnProcessId: 'withoutIncidentsProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 9,
      },
    ],
  },
  {
    bpmnProcessId: 'signalEventProcess',
    tenantId: '<default>',
    processName: 'Signal event',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 20,
    processes: [
      {
        processId: '2251799813686165',
        tenantId: '<default>',
        version: 1,
        name: 'Signal event',
        bpmnProcessId: 'signalEventProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 20,
      },
    ],
  },
  {
    bpmnProcessId: 'intermediate-message-throw-event-process',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 17,
    processes: [
      {
        processId: '2251799813686124',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'intermediate-message-throw-event-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 17,
      },
    ],
  },
  {
    bpmnProcessId: 'terminateEndEvent',
    tenantId: '<default>',
    processName: 'Terminate End Event',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 16,
    processes: [
      {
        processId: '2251799813686155',
        tenantId: '<default>',
        version: 1,
        name: 'Terminate End Event',
        bpmnProcessId: 'terminateEndEvent',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 16,
      },
    ],
  },
  {
    bpmnProcessId: 'dataStoreProcess',
    tenantId: '<default>',
    processName: 'Data store process',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 14,
    processes: [
      {
        processId: '2251799813686159',
        tenantId: '<default>',
        version: 1,
        name: 'Data store process',
        bpmnProcessId: 'dataStoreProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 14,
      },
    ],
  },
  {
    bpmnProcessId: 'message-end-event-process',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 14,
    processes: [
      {
        processId: '2251799813686128',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'message-end-event-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 14,
      },
    ],
  },
  {
    bpmnProcessId: 'timerProcess',
    tenantId: '<default>',
    processName: 'Timer process',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 3,
    processes: [
      {
        processId: '2251799813687198',
        tenantId: '<default>',
        version: 2,
        name: 'Timer process',
        bpmnProcessId: 'timerProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 2,
      },
      {
        processId: '2251799813686143',
        tenantId: '<default>',
        version: 1,
        name: 'Timer process',
        bpmnProcessId: 'timerProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 1,
      },
    ],
  },
  {
    bpmnProcessId: 'bigVarProcess',
    tenantId: '<default>',
    processName: 'Big variable process',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 2,
    processes: [
      {
        processId: '2251799813685430',
        tenantId: '<default>',
        version: 1,
        name: 'Big variable process',
        bpmnProcessId: 'bigVarProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 2,
      },
    ],
  },
  {
    bpmnProcessId: 'Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f',
    tenantId: '<default>',
    processName: 'Input Output Mapping Test',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 1,
    processes: [
      {
        processId: '2251799813685251',
        tenantId: '<default>',
        version: 1,
        name: 'Input Output Mapping Test',
        bpmnProcessId: 'Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 1,
      },
    ],
  },
  {
    bpmnProcessId: 'always-completing-process',
    tenantId: '<default>',
    processName: 'Always completing process',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813685249',
        tenantId: '<default>',
        version: 1,
        name: 'Always completing process',
        bpmnProcessId: 'always-completing-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'intermediate-none-event-process',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686126',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'intermediate-none-event-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'manual-task-process',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686122',
        tenantId: '<default>',
        version: 1,
        name: null,
        bpmnProcessId: 'manual-task-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'noInstancesProcess',
    tenantId: '<default>',
    processName: 'Without Instances Process',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813685253',
        tenantId: '<default>',
        version: 1,
        name: 'Without Instances Process',
        bpmnProcessId: 'noInstancesProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 0,
      },
      {
        processId: '2251799813685255',
        tenantId: '<default>',
        version: 2,
        name: 'Without Instances Process',
        bpmnProcessId: 'noInstancesProcess',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 0,
      },
    ],
  },
  {
    bpmnProcessId: 'undefined-task-process',
    tenantId: '<default>',
    processName: 'undefined-task',
    instancesWithActiveIncidentsCount: 0,
    activeInstancesCount: 0,
    processes: [
      {
        processId: '2251799813686157',
        tenantId: '<default>',
        version: 1,
        name: 'undefined-task',
        bpmnProcessId: 'undefined-task-process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 0,
        activeInstancesCount: 0,
      },
    ],
  },
];

function mockResponses({
  statistics,
  incidentsByError,
  incidentsByProcess,
}: {
  statistics?: CoreStatisticsDto;
  incidentsByError?: IncidentByErrorDto[];
  incidentsByProcess?: ProcessInstanceByNameDto[];
}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
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
      route.request().url().includes('/api/process-instances/core-statistics')
    ) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/incidents/byError')) {
      return route.fulfill({
        status: incidentsByError === undefined ? 400 : 200,
        body: JSON.stringify(incidentsByError),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/incidents/byProcess')) {
      return route.fulfill({
        status: incidentsByProcess === undefined ? 400 : 200,
        body: JSON.stringify(incidentsByProcess),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

export {
  mockStatistics,
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockResponses,
};
