/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper, mockMetaData, mockProcessInstanceDeprecated} from './mocks';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {VariablePanel} from '../index';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

describe('Skeleton', () => {
  it('should display empty content if there are no variables', async () => {
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockFetchProcessDefinitionXml({processDefinitionKey: '123'}).withSuccess(
      mockProcessXml,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstance().withSuccess({
      processInstanceKey: '1',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'StartEvent_1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    });

    flowNodeMetaDataStore.setMetaData(mockMetaData);

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
  });
});
