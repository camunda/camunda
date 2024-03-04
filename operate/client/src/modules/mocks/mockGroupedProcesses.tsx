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
