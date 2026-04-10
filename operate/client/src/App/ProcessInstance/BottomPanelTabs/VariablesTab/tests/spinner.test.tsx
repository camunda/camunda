/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {getWrapper as getBaseWrapper, mockProcessInstance} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const TestSelectionControls: React.FC = () => {
  const {selectElementInstance} = useProcessInstanceElementSelection();

  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'TEST_ELEMENT',
            elementInstanceKey: '2',
          })
        }
      >
        select element instance
      </button>
    </>
  );
};

const getWrapper = (...args: Parameters<typeof getBaseWrapper>) => {
  const BaseWrapper = getBaseWrapper(...args);

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <BaseWrapper>
      <>
        <TestSelectionControls />
        {children}
      </>
    </BaseWrapper>
  );

  return Wrapper;
};

const selectedElementInstance: ElementInstance = {
  elementInstanceKey: '2',
  elementId: 'TEST_ELEMENT',
  elementName: 'Test Element',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  endDate: null,
  processDefinitionId: 'someKey',
  processInstanceKey: '1',
  processDefinitionKey: '2',
  rootProcessInstanceKey: null,
  hasIncident: false,
  incidentKey: null,
  tenantId: '<default>',
};

describe('VariablesTab spinner', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    const statistics = [
      {
        elementId: 'TEST_ELEMENT',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ];

    mockFetchElementInstancesStatistics().withSuccess({
      items: statistics,
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
  });

  it('should display spinner on second variable fetch', async () => {
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchVariables().withDelay({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchVariables().withDelay({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    const {user} = render(<VariablesTab />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    mockFetchElementInstance('2').withSuccess(selectedElementInstance);
    await user.click(
      screen.getByRole('button', {name: /select element instance/i}),
    );

    expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('variables-spinner'),
    );
  });
});
