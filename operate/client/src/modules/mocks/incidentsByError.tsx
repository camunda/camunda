/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

const incidentsByError = [
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
        errorMessage: 'Loan request does not contain all the required data',
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
];

export {incidentsByError};
