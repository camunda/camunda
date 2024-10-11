/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {processInstanceMigrationMappingStore} from './processInstanceMigrationMapping';
import {waitFor} from '@testing-library/react';
import {processesStore} from './processes/processes.migration';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {getSearchString} from 'modules/utils/getSearchString';

// this mocks the URL search string which contains the bpmn process id and version
jest.mock('modules/utils/getSearchString.ts');

/**
 * In these tests a migration mapping from ParticipantMigration_v1.bpmn to ParticipantMigration_v2.bpmn is tested
 *
 * ParticipantMigration_v1.bpmn contains two participants/processes containing different tasks:
 *
 *  - ParticipantMigrationA
 *    - Task A
 *
 *  - ParticipantMigrationB
 *    - Task B
 *
 * ParticipantMigration_v2.bpmn contains two participants/process definitions:
 *
 *  - ParticipantMigrationA
 *    - Task A
 *    - Task C
 *
 *  - ParticipantMigrationB
 *    - Task B
 */
describe('processInstanceMigrationMappingStore', () => {
  afterEach(() => {
    processInstanceMigrationMappingStore.reset();
    processesStore.reset();
  });

  it.each([
    {
      sourceProcess: 'ParticipantMigrationA',
      expectedMapping: {
        sourceFlowNode: {
          id: 'TaskA',
          name: 'Task A',
        },
        selectableTargetFlowNodes: [
          {
            id: 'TaskA',
            name: 'Task A',
          },
          {
            id: 'TaskC',
            name: 'Task C',
          },
        ],
      },
    },
    {
      sourceProcess: 'ParticipantMigrationB',
      expectedMapping: {
        sourceFlowNode: {
          id: 'TaskB',
          name: 'Task B',
        },
        selectableTargetFlowNodes: [
          {
            id: 'TaskB',
            name: 'Task B',
          },
        ],
      },
    },
  ])(
    'should get mappable flow nodes for source $sourceProcess',
    async ({sourceProcess, expectedMapping}) => {
      mockFetchGroupedProcesses().withSuccess([
        {
          bpmnProcessId: 'ParticipantMigrationA',
          name: 'Participant A',
          tenantId: '<default>',
          processes: [
            {
              id: 'ParticipantMigrationA',
              name: 'Participant A',
              version: 1,
              bpmnProcessId: 'ParticipantMigrationA',
              versionTag: null,
            },
            {
              id: 'ParticipantMigrationA',
              name: 'Participant A',
              version: 2,
              bpmnProcessId: 'ParticipantMigrationA',
              versionTag: null,
            },
          ],
          permissions: ['UPDATE_PROCESS_INSTANCE'],
        },
        {
          bpmnProcessId: 'ParticipantMigrationB',
          name: 'Participant B',
          tenantId: '<default>',
          processes: [
            {
              id: 'ParticipantMigrationB',
              name: 'Participant B',
              version: 1,
              bpmnProcessId: 'ParticipantMigrationB',
              versionTag: null,
            },
            {
              id: 'ParticipantMigrationB',
              name: 'Participant B',
              version: 2,
              bpmnProcessId: 'ParticipantMigrationB',
              versionTag: null,
            },
          ],
          permissions: ['UPDATE_PROCESS_INSTANCE'],
        },
      ]);

      // set source process
      // @ts-expect-error
      getSearchString.mockReturnValue({
        process: sourceProcess,
        version: 1,
      });

      processesStore.init();
      processesStore.fetchProcesses();
      await waitFor(() => expect(processesStore.state.status).toBe('fetched'));

      // Fetch migration source diagram
      mockFetchProcessXML().withSuccess(open('ParticipantMigration_v1.bpmn'));
      processXmlMigrationSourceStore.fetchProcessXml();
      await waitFor(() =>
        expect(processXmlMigrationSourceStore.state.status).toBe('fetched'),
      );

      // Fetch migration target diagram
      mockFetchProcessXML().withSuccess(open('ParticipantMigration_v2.bpmn'));
      processXmlMigrationTargetStore.fetchProcessXml();
      await waitFor(() =>
        expect(processXmlMigrationTargetStore.state.status).toBe('fetched'),
      );

      expect(processInstanceMigrationMappingStore.mappableFlowNodes).toEqual([
        expectedMapping,
      ]);
    },
  );
});
