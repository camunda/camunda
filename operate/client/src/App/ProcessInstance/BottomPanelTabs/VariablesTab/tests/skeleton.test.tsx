/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper, mockProcessInstance} from './mocks';
import {VariablesTab} from '../index';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';

const EMPTY_PLACEHOLDER = 'The element has no variables';

describe('Skeleton', () => {
  it('should display empty content if there are no variables', async () => {
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockFetchProcessDefinitionXml({processDefinitionKey: '123'}).withSuccess(
      mockProcessXml,
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchElementInstancesStatistics().withSuccess({
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
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<VariablesTab />, {
      wrapper: getWrapper(),
    });
    expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
  });
});
