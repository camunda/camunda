/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const mockGroupedProcesses = [
  {
    bpmnProcessId: 'bigVarProcess',
    name: 'Big variable process',
    processes: [
      {
        id: '2251799813685511',
        name: 'Big variable process',
        version: 1,
        bpmnProcessId: 'bigVarProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'call-activity-process',
    name: 'Call Activity Process',
    processes: [
      {
        id: '2251799813686217',
        name: 'Call Activity Process',
        version: 1,
        bpmnProcessId: 'call-activity-process',
      },
    ],
  },
  {
    bpmnProcessId: 'called-process',
    name: 'Called Process',
    processes: [
      {
        id: '2251799813687484',
        name: 'Called Process',
        version: 1,
        bpmnProcessId: 'called-process',
      },
    ],
  },
  {
    bpmnProcessId: 'errorProcess',
    name: 'Error Process',
    processes: [
      {
        id: '2251799813686223',
        name: 'Error Process',
        version: 1,
        bpmnProcessId: 'errorProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'eventSubprocessProcess',
    name: 'Event Subprocess Process',
    processes: [
      {
        id: '2251799813686219',
        name: 'Event Subprocess Process',
        version: 1,
        bpmnProcessId: 'eventSubprocessProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'eventBasedGatewayProcess',
    name: 'Event based gateway with timer start',
    processes: [
      {
        id: '2251799813687477',
        name: 'Event based gateway with timer start',
        version: 2,
        bpmnProcessId: 'eventBasedGatewayProcess',
      },
      {
        id: '2251799813686205',
        name: 'Event based gateway with message start',
        version: 1,
        bpmnProcessId: 'eventBasedGatewayProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'flightRegistration',
    name: 'Flight registration',
    processes: [
      {
        id: '2251799813687471',
        name: 'Flight registration',
        version: 2,
        bpmnProcessId: 'flightRegistration',
      },
      {
        id: '2251799813686199',
        name: 'Flight registration',
        version: 1,
        bpmnProcessId: 'flightRegistration',
      },
    ],
  },
  {
    bpmnProcessId: 'multiInstanceProcess',
    name: 'Multi-Instance Process',
    processes: [
      {
        id: '2251799813687473',
        name: 'Multi-Instance Process',
        version: 2,
        bpmnProcessId: 'multiInstanceProcess',
      },
      {
        id: '2251799813686201',
        name: 'Sequential Multi-Instance Process',
        version: 1,
        bpmnProcessId: 'multiInstanceProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'prWithSubprocess',
    name: 'Nested subprocesses',
    processes: [
      {
        id: '2251799813686208',
        name: 'Nested subprocesses',
        version: 1,
        bpmnProcessId: 'prWithSubprocess',
      },
    ],
  },
  {
    bpmnProcessId: 'onlyIncidentsProcess',
    name: 'Only Incidents Process',
    processes: [
      {
        id: '2251799813685335',
        name: 'Only Incidents Process',
        version: 2,
        bpmnProcessId: 'onlyIncidentsProcess',
      },
      {
        id: '2251799813685253',
        name: 'Only Incidents Process',
        version: 1,
        bpmnProcessId: 'onlyIncidentsProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order process',
    processes: [
      {
        id: '2251799813687469',
        name: 'Order process',
        version: 2,
        bpmnProcessId: 'orderProcess',
      },
      {
        id: '2251799813686191',
        name: 'Order process',
        version: 1,
        bpmnProcessId: 'orderProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'timerProcess',
    name: 'Timer process',
    processes: [
      {
        id: '2251799813686214',
        name: 'Timer process',
        version: 1,
        bpmnProcessId: 'timerProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'withoutIncidentsProcess',
    name: 'Without Incidents Process',
    processes: [
      {
        id: '2251799813685428',
        name: 'Without Incidents Process',
        version: 2,
        bpmnProcessId: 'withoutIncidentsProcess',
      },
      {
        id: '2251799813685390',
        name: 'Without Incidents Process',
        version: 1,
        bpmnProcessId: 'withoutIncidentsProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'noInstancesProcess',
    name: 'Without Instances Process',
    processes: [
      {
        id: '2251799813685251',
        name: 'Without Instances Process',
        version: 2,
        bpmnProcessId: 'noInstancesProcess',
      },
      {
        id: '2251799813685249',
        name: 'Without Instances Process',
        version: 1,
        bpmnProcessId: 'noInstancesProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'bigProcess',
    name: null,
    processes: [
      {
        id: '2251799813686221',
        name: null,
        version: 1,
        bpmnProcessId: 'bigProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'complexProcess',
    name: null,
    processes: [
      {
        id: '2251799813689116',
        name: null,
        version: 3,
        bpmnProcessId: 'complexProcess',
      },
      {
        id: '2251799813687475',
        name: null,
        version: 2,
        bpmnProcessId: 'complexProcess',
      },
      {
        id: '2251799813686203',
        name: null,
        version: 1,
        bpmnProcessId: 'complexProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'error-end-process',
    name: null,
    processes: [
      {
        id: '2251799813686225',
        name: null,
        version: 1,
        bpmnProcessId: 'error-end-process',
      },
    ],
  },
  {
    bpmnProcessId: 'interruptingBoundaryEvent',
    name: null,
    processes: [
      {
        id: '2251799813687480',
        name: null,
        version: 2,
        bpmnProcessId: 'interruptingBoundaryEvent',
      },
      {
        id: '2251799813686210',
        name: null,
        version: 1,
        bpmnProcessId: 'interruptingBoundaryEvent',
      },
    ],
  },
  {
    bpmnProcessId: 'loanProcess',
    name: null,
    processes: [
      {
        id: '2251799813686197',
        name: null,
        version: 1,
        bpmnProcessId: 'loanProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'nonInterruptingBoundaryEvent',
    name: null,
    processes: [
      {
        id: '2251799813687482',
        name: null,
        version: 2,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
      },
      {
        id: '2251799813686212',
        name: null,
        version: 1,
        bpmnProcessId: 'nonInterruptingBoundaryEvent',
      },
    ],
  },
];

export {mockGroupedProcesses};
