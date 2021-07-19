/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';

import {Dashboard as DashboardComponent} from './index';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {useEffect} from 'react';

export default {
  title: 'Pages/Dashboard',
};

const Dashboard: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <DashboardComponent />
    </MemoryRouter>
  );
};

Dashboard.storyName = 'Dashboard - Success';
Dashboard.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(
        ctx.json({
          running: 1087,
          active: 210,
          withIncidents: 877,
        })
      );
    }),
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(
        ctx.json([
          {
            bpmnProcessId: 'complexProcess',
            processName: null,
            instancesWithActiveIncidentsCount: 164,
            activeInstancesCount: 28,
            processes: [
              {
                processId: '2251799813689530',
                version: 3,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 106,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686021',
                version: 1,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 32,
                activeInstancesCount: 17,
              },
              {
                processId: '2251799813687833',
                version: 2,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 26,
                activeInstancesCount: 11,
              },
            ],
          },
          {
            bpmnProcessId: 'flightRegistration',
            processName: 'Flight registration',
            instancesWithActiveIncidentsCount: 147,
            activeInstancesCount: 3,
            processes: [
              {
                processId: '2251799813687829',
                version: 2,
                name: 'Flight registration',
                bpmnProcessId: 'flightRegistration',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 75,
                activeInstancesCount: 3,
              },
              {
                processId: '2251799813686017',
                version: 1,
                name: 'Flight registration',
                bpmnProcessId: 'flightRegistration',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 72,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'orderProcess',
            processName: 'Order process',
            instancesWithActiveIncidentsCount: 116,
            activeInstancesCount: 6,
            processes: [
              {
                processId: '2251799813686009',
                version: 1,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 60,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687827',
                version: 2,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 56,
                activeInstancesCount: 6,
              },
            ],
          },
          {
            bpmnProcessId: 'loanProcess',
            processName: null,
            instancesWithActiveIncidentsCount: 79,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813686014',
                version: 1,
                name: null,
                bpmnProcessId: 'loanProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 79,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'multiInstanceProcess',
            processName: 'Multi-Instance Process',
            instancesWithActiveIncidentsCount: 76,
            activeInstancesCount: 91,
            processes: [
              {
                processId: '2251799813687831',
                version: 2,
                name: 'Multi-Instance Process',
                bpmnProcessId: 'multiInstanceProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 76,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686019',
                version: 1,
                name: 'Sequential Multi-Instance Process',
                bpmnProcessId: 'multiInstanceProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 91,
              },
            ],
          },
          {
            bpmnProcessId: 'call-activity-process',
            processName: 'Call Activity Process',
            instancesWithActiveIncidentsCount: 54,
            activeInstancesCount: 37,
            processes: [
              {
                processId: '2251799813686035',
                version: 1,
                name: 'Call Activity Process',
                bpmnProcessId: 'call-activity-process',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 54,
                activeInstancesCount: 37,
              },
            ],
          },
          {
            bpmnProcessId: 'error-end-process',
            processName: null,
            instancesWithActiveIncidentsCount: 54,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813686043',
                version: 1,
                name: null,
                bpmnProcessId: 'error-end-process',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 54,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'prWithSubprocess',
            processName: 'Nested subprocesses',
            instancesWithActiveIncidentsCount: 51,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813686026',
                version: 1,
                name: 'Nested subprocesses',
                bpmnProcessId: 'prWithSubprocess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 51,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'errorProcess',
            processName: 'Error Process',
            instancesWithActiveIncidentsCount: 48,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813686041',
                version: 1,
                name: 'Error Process',
                bpmnProcessId: 'errorProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 48,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            processName: 'Event based gateway with timer start',
            instancesWithActiveIncidentsCount: 26,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813687835',
                version: 2,
                name: 'Event based gateway with timer start',
                bpmnProcessId: 'eventBasedGatewayProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 20,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686023',
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
            bpmnProcessId: 'nonInterruptingBoundaryEvent',
            processName: null,
            instancesWithActiveIncidentsCount: 25,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813687840',
                version: 2,
                name: null,
                bpmnProcessId: 'nonInterruptingBoundaryEvent',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 22,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686030',
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
            bpmnProcessId: 'called-process',
            processName: 'Called Process',
            instancesWithActiveIncidentsCount: 16,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813687842',
                version: 1,
                name: 'Called Process',
                bpmnProcessId: 'called-process',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 16,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'eventSubprocessProcess',
            processName: 'Event Subprocess Process',
            instancesWithActiveIncidentsCount: 15,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813686037',
                version: 1,
                name: 'Event Subprocess Process',
                bpmnProcessId: 'eventSubprocessProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 15,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'onlyIncidentsProcess',
            processName: 'Only Incidents Process',
            instancesWithActiveIncidentsCount: 13,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813685253',
                version: 1,
                name: 'Only Incidents Process',
                bpmnProcessId: 'onlyIncidentsProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 7,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813685276',
                version: 2,
                name: 'Only Incidents Process',
                bpmnProcessId: 'onlyIncidentsProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 6,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'interruptingBoundaryEvent',
            processName: null,
            instancesWithActiveIncidentsCount: 8,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813687838',
                version: 2,
                name: null,
                bpmnProcessId: 'interruptingBoundaryEvent',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 8,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686028',
                version: 1,
                name: null,
                bpmnProcessId: 'interruptingBoundaryEvent',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            bpmnProcessId: 'bigProcess',
            processName: null,
            instancesWithActiveIncidentsCount: 1,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813686039',
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
            bpmnProcessId: 'withoutIncidentsProcess',
            processName: 'Without Incidents Process',
            instancesWithActiveIncidentsCount: 0,
            activeInstancesCount: 12,
            processes: [
              {
                processId: '2251799813685294',
                version: 1,
                name: 'Without Incidents Process',
                bpmnProcessId: 'withoutIncidentsProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 6,
              },
              {
                processId: '2251799813685307',
                version: 2,
                name: 'Without Incidents Process',
                bpmnProcessId: 'withoutIncidentsProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 6,
              },
            ],
          },
          {
            bpmnProcessId: 'bigVarProcess',
            processName: 'Big variable process',
            instancesWithActiveIncidentsCount: 0,
            activeInstancesCount: 2,
            processes: [
              {
                processId: '2251799813685329',
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
            bpmnProcessId: 'timerProcess',
            processName: 'Timer process',
            instancesWithActiveIncidentsCount: 0,
            activeInstancesCount: 2,
            processes: [
              {
                processId: '2251799813686032',
                version: 1,
                name: 'Timer process',
                bpmnProcessId: 'timerProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 2,
              },
            ],
          },
          {
            bpmnProcessId: 'noInstancesProcess',
            processName: 'Without Instances Process',
            instancesWithActiveIncidentsCount: 0,
            activeInstancesCount: 0,
            processes: [
              {
                processId: '2251799813685249',
                version: 1,
                name: 'Without Instances Process',
                bpmnProcessId: 'noInstancesProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813685251',
                version: 2,
                name: 'Without Instances Process',
                bpmnProcessId: 'noInstancesProcess',
                errorMessage: null,
                instancesWithActiveIncidentsCount: 0,
                activeInstancesCount: 0,
              },
            ],
          },
        ])
      );
    }),
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(
        ctx.json([
          {
            errorMessage: 'No more retries left.',
            instancesWithErrorCount: 376,
            processes: [
              {
                processId: '2251799813687747',
                version: 2,
                name: 'Multi-Instance Process',
                bpmnProcessId: 'multiInstanceProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 58,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686157',
                version: 1,
                name: 'Nested subprocesses',
                bpmnProcessId: 'prWithSubprocess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 51,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686148',
                version: 1,
                name: 'Flight registration',
                bpmnProcessId: 'flightRegistration',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 49,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687745',
                version: 2,
                name: 'Flight registration',
                bpmnProcessId: 'flightRegistration',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 49,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686152',
                version: 1,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 38,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687749',
                version: 2,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 22,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686168',
                version: 1,
                name: 'Event Subprocess Process',
                bpmnProcessId: 'eventSubprocessProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 22,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687756',
                version: 2,
                name: null,
                bpmnProcessId: 'nonInterruptingBoundaryEvent',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 19,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687758',
                version: 1,
                name: 'Called Process',
                bpmnProcessId: 'called-process',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 15,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687743',
                version: 2,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 14,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687754',
                version: 2,
                name: null,
                bpmnProcessId: 'interruptingBoundaryEvent',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 11,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686146',
                version: 1,
                name: null,
                bpmnProcessId: 'loanProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 11,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686141',
                version: 1,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 9,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686154',
                version: 1,
                name: 'Event based gateway with message start',
                bpmnProcessId: 'eventBasedGatewayProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 5,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686161',
                version: 1,
                name: null,
                bpmnProcessId: 'nonInterruptingBoundaryEvent',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 2,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813686170',
                version: 1,
                name: null,
                bpmnProcessId: 'bigProcess',
                errorMessage: 'No more retries left.',
                instancesWithActiveIncidentsCount: 1,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
            instancesWithErrorCount: 90,
            processes: [
              {
                processId: '2251799813689070',
                version: 3,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage:
                  "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
                instancesWithActiveIncidentsCount: 70,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687751',
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
            instancesWithErrorCount: 70,
            processes: [
              {
                processId: '2251799813689070',
                version: 3,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage:
                  'Expected at least one condition to evaluate to true, or to have a default flow',
                instancesWithActiveIncidentsCount: 70,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              'Something went wrong. \njava.lang.Throwable\n\tat io.camunda.operate.data.usertest.UserTestDataGenerator.lambda$progressAlwaysFailingTask$3(UserTestDataGenerator.java:338)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:830)',
            instancesWithErrorCount: 70,
            processes: [
              {
                processId: '2251799813689070',
                version: 3,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage:
                  'Something went wrong. \njava.lang.Throwable\n\tat io.camunda.operate.data.usertest.UserTestDataGenerator.lambda$progressAlwaysFailingTask$3(UserTestDataGenerator.java:338)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:830)',
                instancesWithActiveIncidentsCount: 70,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'",
            instancesWithErrorCount: 70,
            processes: [
              {
                processId: '2251799813689070',
                version: 3,
                name: null,
                bpmnProcessId: 'complexProcess',
                errorMessage:
                  "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'",
                instancesWithActiveIncidentsCount: 70,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
            instancesWithErrorCount: 55,
            processes: [
              {
                processId: '2251799813686166',
                version: 1,
                name: 'Call Activity Process',
                bpmnProcessId: 'call-activity-process',
                errorMessage:
                  "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
                instancesWithActiveIncidentsCount: 55,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              "Expected to throw an error event with the code 'end', but it was not caught.",
            instancesWithErrorCount: 55,
            processes: [
              {
                processId: '2251799813686174',
                version: 1,
                name: null,
                bpmnProcessId: 'error-end-process',
                errorMessage:
                  "Expected to throw an error event with the code 'end', but it was not caught.",
                instancesWithActiveIncidentsCount: 55,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'Job worker throw error with error code: unknown',
            instancesWithErrorCount: 53,
            processes: [
              {
                processId: '2251799813686172',
                version: 1,
                name: 'Error Process',
                bpmnProcessId: 'errorProcess',
                errorMessage: 'Job worker throw error with error code: unknown',
                instancesWithActiveIncidentsCount: 53,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'Cannot connect to server delivery05',
            instancesWithErrorCount: 37,
            processes: [
              {
                processId: '2251799813686141',
                version: 1,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage: 'Cannot connect to server delivery05',
                instancesWithActiveIncidentsCount: 22,
                activeInstancesCount: 0,
              },
              {
                processId: '2251799813687743',
                version: 2,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage: 'Cannot connect to server delivery05',
                instancesWithActiveIncidentsCount: 15,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'Loan request does not contain all the required data',
            instancesWithErrorCount: 28,
            processes: [
              {
                processId: '2251799813686146',
                version: 1,
                name: null,
                bpmnProcessId: 'loanProcess',
                errorMessage:
                  'Loan request does not contain all the required data',
                instancesWithActiveIncidentsCount: 28,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'Schufa system is not accessible',
            instancesWithErrorCount: 19,
            processes: [
              {
                processId: '2251799813686146',
                version: 1,
                name: null,
                bpmnProcessId: 'loanProcess',
                errorMessage: 'Schufa system is not accessible',
                instancesWithActiveIncidentsCount: 19,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              "failed to evaluate expression 'paid = false': no variable found for name 'paid'",
            instancesWithErrorCount: 11,
            processes: [
              {
                processId: '2251799813687743',
                version: 2,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage:
                  "failed to evaluate expression 'paid = false': no variable found for name 'paid'",
                instancesWithActiveIncidentsCount: 11,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage:
              "failed to evaluate expression 'paid = true': no variable found for name 'paid'",
            instancesWithErrorCount: 8,
            processes: [
              {
                processId: '2251799813686141',
                version: 1,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage:
                  "failed to evaluate expression 'paid = true': no variable found for name 'paid'",
                instancesWithActiveIncidentsCount: 8,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'No space left on device.',
            instancesWithErrorCount: 4,
            processes: [
              {
                processId: '2251799813685321',
                version: 2,
                name: 'Only Incidents Process',
                bpmnProcessId: 'onlyIncidentsProcess',
                errorMessage: 'No space left on device.',
                instancesWithActiveIncidentsCount: 4,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'Cannot connect to server fly-host',
            instancesWithErrorCount: 1,
            processes: [
              {
                processId: '2251799813687745',
                version: 2,
                name: 'Flight registration',
                bpmnProcessId: 'flightRegistration',
                errorMessage: 'Cannot connect to server fly-host',
                instancesWithActiveIncidentsCount: 1,
                activeInstancesCount: 0,
              },
            ],
          },
          {
            errorMessage: 'No memory left.',
            instancesWithErrorCount: 1,
            processes: [
              {
                processId: '2251799813685253',
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
            errorMessage: 'error',
            instancesWithErrorCount: 1,
            processes: [
              {
                processId: '2251799813686161',
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
              'java.lang.RuntimeException: Payment system not available.\n\tat io.camunda.operate.data.develop.DevelopDataGenerator.lambda$progressOrderProcessCheckPayment$0(DevelopDataGenerator.java:228)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:830)',
            instancesWithErrorCount: 1,
            processes: [
              {
                processId: '2251799813687743',
                version: 2,
                name: 'Order process',
                bpmnProcessId: 'orderProcess',
                errorMessage:
                  'java.lang.RuntimeException: Payment system not available.\n\tat io.camunda.operate.data.develop.DevelopDataGenerator.lambda$progressOrderProcessCheckPayment$0(DevelopDataGenerator.java:228)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.executeJob(JobRunnableFactory.java:44)\n\tat io.camunda.zeebe.client.impl.worker.JobRunnableFactory.lambda$create$0(JobRunnableFactory.java:39)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:830)',
                instancesWithActiveIncidentsCount: 1,
                activeInstancesCount: 0,
              },
            ],
          },
        ])
      );
    }),
  ],
};

const DashboardSkeleton: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <DashboardComponent />
    </MemoryRouter>
  );
};

DashboardSkeleton.storyName = 'Dashboard - Skeleton';
DashboardSkeleton.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json([]));
    }),
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json([]));
    }),
  ],
};

const DashboardFailure: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <DashboardComponent />
    </MemoryRouter>
  );
};

DashboardFailure.storyName = 'Dashboard - Failure';
DashboardFailure.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json({}));
    }),
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json([]));
    }),
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json([]));
    }),
  ],
};

export {Dashboard, DashboardSkeleton, DashboardFailure};
