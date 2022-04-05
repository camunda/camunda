/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const flowNodeInstances = {
  '2251799813686430': {
    children: [
      {
        id: '2251799813686434',
        type: 'START_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'StartEvent_1',
        startDate: '2021-08-23T08:37:27.133+0000',
        endDate: '2021-08-23T08:37:27.137+0000',
        treePath: '2251799813686430/2251799813686434',
        sortValues: [1629707847133, '2251799813686434'],
      },
      {
        id: '2251799813686436',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:37:27.145+0000',
        endDate: '2021-08-23T08:38:27.067+0000',
        treePath: '2251799813686430/2251799813686436',
        sortValues: [1629707847145, '2251799813686436'],
      },
      {
        id: '2251799813688568',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:27.080+0000',
        endDate: '2021-08-23T08:38:27.080+0000',
        treePath: '2251799813686430/2251799813688568',
        sortValues: [1629707907080, '2251799813688568'],
      },
      {
        id: '2251799813688574',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:27.102+0000',
        endDate: '2021-08-23T08:38:37.076+0000',
        treePath: '2251799813686430/2251799813688574',
        sortValues: [1629707907102, '2251799813688574'],
      },
      {
        id: '2251799813688930',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:29.125+0000',
        endDate: '2021-08-23T08:38:29.137+0000',
        treePath: '2251799813686430/2251799813688930',
        sortValues: [1629707909125, '2251799813688930'],
      },
      {
        id: '2251799813688932',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:29.145+0000',
        endDate: '2021-08-23T08:38:29.797+0000',
        treePath: '2251799813686430/2251799813688932',
        sortValues: [1629707909145, '2251799813688932'],
      },
      {
        id: '2251799813689006',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:29.809+0000',
        endDate: '2021-08-23T08:38:29.809+0000',
        treePath: '2251799813686430/2251799813689006',
        sortValues: [1629707909809, '2251799813689006'],
      },
      {
        id: '2251799813689010',
        type: 'MULTI_INSTANCE_BODY',
        state: 'INCIDENT',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:29.812+0000',
        endDate: null,
        treePath: '2251799813686430/2251799813689010',
        sortValues: [1629707909812, '2251799813689010'],
      },
      {
        id: '2251799813689404',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:31.839+0000',
        endDate: '2021-08-23T08:38:31.846+0000',
        treePath: '2251799813686430/2251799813689404',
        sortValues: [1629707911839, '2251799813689404'],
      },
      {
        id: '2251799813689406',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:31.848+0000',
        endDate: '2021-08-23T08:38:32.358+0000',
        treePath: '2251799813686430/2251799813689406',
        sortValues: [1629707911848, '2251799813689406'],
      },
      {
        id: '2251799813689468',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:32.378+0000',
        endDate: '2021-08-23T08:38:32.378+0000',
        treePath: '2251799813686430/2251799813689468',
        sortValues: [1629707912378, '2251799813689468'],
      },
      {
        id: '2251799813689472',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:32.394+0000',
        endDate: '2021-08-23T08:38:47.492+0000',
        treePath: '2251799813686430/2251799813689472',
        sortValues: [1629707912394, '2251799813689472'],
      },
      {
        id: '2251799813689837',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:34.423+0000',
        endDate: '2021-08-23T08:38:34.428+0000',
        treePath: '2251799813686430/2251799813689837',
        sortValues: [1629707914423, '2251799813689837'],
      },
      {
        id: '2251799813689839',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:34.434+0000',
        endDate: '2021-08-23T08:38:34.900+0000',
        treePath: '2251799813686430/2251799813689839',
        sortValues: [1629707914434, '2251799813689839'],
      },
      {
        id: '2251799813689902',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:34.906+0000',
        endDate: '2021-08-23T08:38:34.906+0000',
        treePath: '2251799813686430/2251799813689902',
        sortValues: [1629707914906, '2251799813689902'],
      },
      {
        id: '2251799813689904',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:34.909+0000',
        endDate: '2021-08-23T08:38:48.796+0000',
        treePath: '2251799813686430/2251799813689904',
        sortValues: [1629707914909, '2251799813689904'],
      },
      {
        id: '2251799813690330',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:36.921+0000',
        endDate: '2021-08-23T08:38:36.928+0000',
        treePath: '2251799813686430/2251799813690330',
        sortValues: [1629707916921, '2251799813690330'],
      },
      {
        id: '2251799813690332',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:36.932+0000',
        endDate: '2021-08-23T08:38:37.271+0000',
        treePath: '2251799813686430/2251799813690332',
        sortValues: [1629707916932, '2251799813690332'],
      },
      {
        id: '2251799813690364',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:37.277+0000',
        endDate: '2021-08-23T08:38:37.277+0000',
        treePath: '2251799813686430/2251799813690364',
        sortValues: [1629707917277, '2251799813690364'],
      },
      {
        id: '2251799813690366',
        type: 'MULTI_INSTANCE_BODY',
        state: 'INCIDENT',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:37.278+0000',
        endDate: null,
        treePath: '2251799813686430/2251799813690366',
        sortValues: [1629707917278, '2251799813690366'],
      },
      {
        id: '2251799813690765',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:39.296+0000',
        endDate: '2021-08-23T08:38:39.303+0000',
        treePath: '2251799813686430/2251799813690765',
        sortValues: [1629707919296, '2251799813690765'],
      },
      {
        id: '2251799813690768',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:39.318+0000',
        endDate: '2021-08-23T08:38:39.712+0000',
        treePath: '2251799813686430/2251799813690768',
        sortValues: [1629707919318, '2251799813690768'],
      },
      {
        id: '2251799813690801',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:39.718+0000',
        endDate: '2021-08-23T08:38:39.718+0000',
        treePath: '2251799813686430/2251799813690801',
        sortValues: [1629707919718, '2251799813690801'],
      },
      {
        id: '2251799813690805',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:39.733+0000',
        endDate: '2021-08-23T08:38:50.538+0000',
        treePath: '2251799813686430/2251799813690805',
        sortValues: [1629707919733, '2251799813690805'],
      },
      {
        id: '2251799813691124',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:41.759+0000',
        endDate: '2021-08-23T08:38:41.765+0000',
        treePath: '2251799813686430/2251799813691124',
        sortValues: [1629707921759, '2251799813691124'],
      },
      {
        id: '2251799813691126',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:41.768+0000',
        endDate: '2021-08-23T08:38:42.032+0000',
        treePath: '2251799813686430/2251799813691126',
        sortValues: [1629707921768, '2251799813691126'],
      },
      {
        id: '2251799813691141',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:42.036+0000',
        endDate: '2021-08-23T08:38:42.036+0000',
        treePath: '2251799813686430/2251799813691141',
        sortValues: [1629707922036, '2251799813691141'],
      },
      {
        id: '2251799813691143',
        type: 'MULTI_INSTANCE_BODY',
        state: 'INCIDENT',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:42.036+0000',
        endDate: null,
        treePath: '2251799813686430/2251799813691143',
        sortValues: [1629707922036, '2251799813691143'],
      },
      {
        id: '2251799813691451',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:44.078+0000',
        endDate: '2021-08-23T08:38:44.090+0000',
        treePath: '2251799813686430/2251799813691451',
        sortValues: [1629707924078, '2251799813691451'],
      },
      {
        id: '2251799813691453',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:44.095+0000',
        endDate: '2021-08-23T08:38:44.401+0000',
        treePath: '2251799813686430/2251799813691453',
        sortValues: [1629707924095, '2251799813691453'],
      },
      {
        id: '2251799813691467',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:44.419+0000',
        endDate: '2021-08-23T08:38:44.419+0000',
        treePath: '2251799813686430/2251799813691467',
        sortValues: [1629707924419, '2251799813691467'],
      },
      {
        id: '2251799813691469',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:44.421+0000',
        endDate: '2021-08-23T08:38:58.918+0000',
        treePath: '2251799813686430/2251799813691469',
        sortValues: [1629707924421, '2251799813691469'],
      },
      {
        id: '2251799813691773',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:46.470+0000',
        endDate: '2021-08-23T08:38:46.473+0000',
        treePath: '2251799813686430/2251799813691773',
        sortValues: [1629707926470, '2251799813691773'],
      },
      {
        id: '2251799813691775',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:46.479+0000',
        endDate: '2021-08-23T08:38:46.703+0000',
        treePath: '2251799813686430/2251799813691775',
        sortValues: [1629707926479, '2251799813691775'],
      },
      {
        id: '2251799813691808',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:46.708+0000',
        endDate: '2021-08-23T08:38:46.708+0000',
        treePath: '2251799813686430/2251799813691808',
        sortValues: [1629707926708, '2251799813691808'],
      },
      {
        id: '2251799813691810',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:46.724+0000',
        endDate: '2021-08-23T08:38:59.953+0000',
        treePath: '2251799813686430/2251799813691810',
        sortValues: [1629707926724, '2251799813691810'],
      },
      {
        id: '2251799813692103',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:48.752+0000',
        endDate: '2021-08-23T08:38:48.761+0000',
        treePath: '2251799813686430/2251799813692103',
        sortValues: [1629707928752, '2251799813692103'],
      },
      {
        id: '2251799813692105',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:48.767+0000',
        endDate: '2021-08-23T08:38:48.900+0000',
        treePath: '2251799813686430/2251799813692105',
        sortValues: [1629707928767, '2251799813692105'],
      },
      {
        id: '2251799813692114',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:48.902+0000',
        endDate: '2021-08-23T08:38:48.902+0000',
        treePath: '2251799813686430/2251799813692114',
        sortValues: [1629707928902, '2251799813692114'],
      },
      {
        id: '2251799813692116',
        type: 'MULTI_INSTANCE_BODY',
        state: 'INCIDENT',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:48.907+0000',
        endDate: null,
        treePath: '2251799813686430/2251799813692116',
        sortValues: [1629707928907, '2251799813692116'],
      },
      {
        id: '2251799813692375',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:50.990+0000',
        endDate: '2021-08-23T08:38:51.006+0000',
        treePath: '2251799813686430/2251799813692375',
        sortValues: [1629707930990, '2251799813692375'],
      },
      {
        id: '2251799813692377',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:51.014+0000',
        endDate: '2021-08-23T08:38:51.154+0000',
        treePath: '2251799813686430/2251799813692377',
        sortValues: [1629707931014, '2251799813692377'],
      },
      {
        id: '2251799813692385',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:51.165+0000',
        endDate: '2021-08-23T08:38:51.165+0000',
        treePath: '2251799813686430/2251799813692385',
        sortValues: [1629707931165, '2251799813692385'],
      },
      {
        id: '2251799813692387',
        type: 'MULTI_INSTANCE_BODY',
        state: 'COMPLETED',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:51.177+0000',
        endDate: '2021-08-23T08:39:01.841+0000',
        treePath: '2251799813686430/2251799813692387',
        sortValues: [1629707931177, '2251799813692387'],
      },
      {
        id: '2251799813692653',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:53.218+0000',
        endDate: '2021-08-23T08:38:53.265+0000',
        treePath: '2251799813686430/2251799813692653',
        sortValues: [1629707933218, '2251799813692653'],
      },
      {
        id: '2251799813692658',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:53.300+0000',
        endDate: '2021-08-23T08:38:54.285+0000',
        treePath: '2251799813686430/2251799813692658',
        sortValues: [1629707933300, '2251799813692658'],
      },
      {
        id: '2251799813692725',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_0cjcl1k',
        startDate: '2021-08-23T08:38:54.307+0000',
        endDate: '2021-08-23T08:38:54.307+0000',
        treePath: '2251799813686430/2251799813692725',
        sortValues: [1629707934307, '2251799813692725'],
      },
      {
        id: '2251799813692728',
        type: 'MULTI_INSTANCE_BODY',
        state: 'INCIDENT',
        flowNodeId: 'taskB',
        startDate: '2021-08-23T08:38:54.312+0000',
        endDate: null,
        treePath: '2251799813686430/2251799813692728',
        sortValues: [1629707934312, '2251799813692728'],
      },
      {
        id: '2251799813693092',
        type: 'BOUNDARY_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'Event_06lbs4q',
        startDate: '2021-08-23T08:38:56.341+0000',
        endDate: '2021-08-23T08:38:56.366+0000',
        treePath: '2251799813686430/2251799813693092',
        sortValues: [1629707936341, '2251799813693092'],
      },
      {
        id: '2251799813693095',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'taskA',
        startDate: '2021-08-23T08:38:56.376+0000',
        endDate: '2021-08-23T08:38:56.860+0000',
        treePath: '2251799813686430/2251799813693095',
        sortValues: [1629707936376, '2251799813693095'],
      },
    ],
    running: null,
  },
};

const flowNodeStates = {
  Event_06lbs4q: 'COMPLETED',
  StartEvent_1: 'COMPLETED',
  taskA: 'COMPLETED',
  taskB: 'INCIDENT',
  ExclusiveGateway_0cjcl1k: 'COMPLETED',
};

const incidents = {
  count: 8,
  incidents: [
    {
      id: '2251799813691403',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813689054',
      jobId: '2251799813689191',
      creationTime: '2021-08-23T08:38:42.983+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813692335',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813690398',
      jobId: '2251799813690511',
      creationTime: '2021-08-23T08:38:49.523+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813692369',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813691167',
      jobId: '2251799813691264',
      creationTime: '2021-08-23T08:38:50.839+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813693739',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813692128',
      jobId: '2251799813692203',
      creationTime: '2021-08-23T08:39:00.086+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813694013',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813692732',
      jobId: '2251799813692789',
      creationTime: '2021-08-23T08:39:01.867+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813694035',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813692779',
      jobId: '2251799813692930',
      creationTime: '2021-08-23T08:39:02.568+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813694059',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813693210',
      jobId: '2251799813693354',
      creationTime: '2021-08-23T08:39:03.268+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
    {
      id: '2251799813694552',
      errorType: 'No more retries left',
      errorMessage: 'No more retries left.',
      flowNodeId: 'taskB',
      flowNodeInstanceId: '2251799813694110',
      jobId: '2251799813694230',
      creationTime: '2021-08-23T08:39:06.062+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
  ],
  errorTypes: [
    {
      errorType: 'No more retries left',
      count: 8,
    },
  ],
  flowNodes: [
    {
      flowNodeId: 'taskB',
      count: 8,
    },
  ],
};

const instance = {
  id: '2251799813686430',
  processId: '2251799813685486',
  processName: 'bigProcess',
  processVersion: 1,
  startDate: '2021-08-23T08:37:27.125+0000',
  endDate: null,
  state: 'INCIDENT',
  bpmnProcessId: 'bigProcess',
  hasActiveOperation: false,
  operations: [],
  parentInstanceId: null,
  sortValues: null,
  breadcrumb: [],
};

const sequenceFlows = [
  {
    processInstanceId: '2251799813686430',
    activityId: 'SequenceFlow_0j3eksh',
  },
  {
    processInstanceId: '2251799813686430',
    activityId: 'SequenceFlow_0wumr00',
  },
  {
    processInstanceId: '2251799813686430',
    activityId: 'SequenceFlow_1gi2xny',
  },
  {
    processInstanceId: '2251799813686430',
    activityId: 'SequenceFlow_1jeebm1',
  },
];

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_0dkpwbm" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.9.1">
  <bpmn:process id="bigProcess" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>SequenceFlow_0wumr00</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:exclusiveGateway id="ExclusiveGateway_0cjcl1k" name="Continue?">
      <bpmn:incoming>SequenceFlow_0j3eksh</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1jeebm1</bpmn:outgoing>
      <bpmn:outgoing>SequenceFlow_07u0xkc</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="SequenceFlow_0wumr00" sourceRef="StartEvent_1" targetRef="taskA" />
    <bpmn:sequenceFlow id="SequenceFlow_0j3eksh" sourceRef="taskA" targetRef="ExclusiveGateway_0cjcl1k" />
    <bpmn:sequenceFlow id="SequenceFlow_1jeebm1" name="yes" sourceRef="ExclusiveGateway_0cjcl1k" targetRef="taskB">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=i&lt;=loopCardinality</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="SequenceFlow_1gi2xny" sourceRef="Event_06lbs4q" targetRef="taskA" />
    <bpmn:endEvent id="EndEvent_02f1al1">
      <bpmn:incoming>SequenceFlow_07u0xkc</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_07u0xkc" name="no" sourceRef="ExclusiveGateway_0cjcl1k" targetRef="EndEvent_02f1al1">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=i&gt;loopCardinality</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:serviceTask id="taskA" name="Task A">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="bigProcessTaskA" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0wumr00</bpmn:incoming>
      <bpmn:incoming>SequenceFlow_1gi2xny</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0j3eksh</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="taskB" name="Task B">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="bigProcessTaskB" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_1jeebm1</bpmn:incoming>
      <bpmn:multiInstanceLoopCharacteristics>
        <bpmn:extensionElements>
          <zeebe:loopCharacteristics inputCollection="=clients" inputElement="client" />
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
    </bpmn:serviceTask>
    <bpmn:boundaryEvent id="Event_06lbs4q" cancelActivity="false" attachedToRef="taskB">
      <bpmn:outgoing>SequenceFlow_1gi2xny</bpmn:outgoing>
      <bpmn:timerEventDefinition id="TimerEventDefinition_0qjwf46">
        <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT2S</bpmn:timeDuration>
      </bpmn:timerEventDefinition>
    </bpmn:boundaryEvent>
  </bpmn:process>
  <bpmn:error id="Error_06pg7eb" name="taskBFailed" errorCode="taskBFailed" />
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="bigProcess">
      <bpmndi:BPMNEdge id="SequenceFlow_07u0xkc_di" bpmnElement="SequenceFlow_07u0xkc">
        <di:waypoint x="540" y="195" />
        <di:waypoint x="540" y="350" />
        <di:waypoint x="692" y="350" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="549" y="270" width="13" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1gi2xny_di" bpmnElement="SequenceFlow_1gi2xny">
        <di:waypoint x="730" y="112" />
        <di:waypoint x="730" y="80" />
        <di:waypoint x="380" y="80" />
        <di:waypoint x="380" y="130" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1jeebm1_di" bpmnElement="SequenceFlow_1jeebm1">
        <di:waypoint x="565" y="170" />
        <di:waypoint x="670" y="170" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="609" y="152" width="17" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0j3eksh_di" bpmnElement="SequenceFlow_0j3eksh">
        <di:waypoint x="430" y="170" />
        <di:waypoint x="515" y="170" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0wumr00_di" bpmnElement="SequenceFlow_0wumr00">
        <di:waypoint x="178" y="170" />
        <di:waypoint x="330" y="170" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="142" y="152" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ExclusiveGateway_0cjcl1k_di" bpmnElement="ExclusiveGateway_0cjcl1k" isMarkerVisible="true">
        <dc:Bounds x="515" y="145" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="514" y="133" width="51" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_02f1al1_di" bpmnElement="EndEvent_02f1al1">
        <dc:Bounds x="692" y="332" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0s6kryf_di" bpmnElement="taskA">
        <dc:Bounds x="330" y="130" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_185s3ko_di" bpmnElement="taskB">
        <dc:Bounds x="670" y="130" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0x66g4j_di" bpmnElement="Event_06lbs4q">
        <dc:Bounds x="712" y="112" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
  `;

const flowNodeMetadata = {
  flowNodeInstanceId: null,
  flowNodeId: 'taskB',
  flowNodeType: 'MULTI_INSTANCE_BODY',
  instanceCount: 17,
  breadcrumb: [],
  instanceMetadata: null,
};

export {
  flowNodeInstances,
  flowNodeStates,
  incidents,
  instance,
  sequenceFlows,
  xml,
  flowNodeMetadata,
};
