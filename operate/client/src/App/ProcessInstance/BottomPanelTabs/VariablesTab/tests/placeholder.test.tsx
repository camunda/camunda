/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {render, screen} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {getWrapper as getBaseWrapper, mockProcessInstance} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

const TestSelectionControls: React.FC = () => {
  const {selectElement, selectElementInstance} =
    useProcessInstanceElementSelection();

  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElement({
            elementId: 'TEST_ELEMENT',
          })
        }
      >
        select element
      </button>
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

const selectedElementInstance = {
  elementInstanceKey: '2',
  elementId: 'TEST_ELEMENT',
  elementName: 'Test Element',
  type: 'SERVICE_TASK' as const,
  state: 'ACTIVE' as const,
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

describe('VariablesTab placeholders', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    const statistics = [
      {
        elementId: 'TEST_ELEMENT',
        active: 1,
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
    mockFetchElementInstancesStatistics().withSuccess({
      items: statistics,
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
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

  it.each([true, false])(
    'should show multiple scope placeholder when multiple nodes are selected - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      mockSearchElementInstances().withSuccess({
        items: [
          selectedElementInstance,
          {
            ...selectedElementInstance,
            elementInstanceKey: '3',
          },
        ],
        page: {
          totalItems: 2,
          startCursor: null,
          endCursor: null,
          hasMoreTotalItems: false,
        },
      });
      mockSearchVariables().withSuccess({
        items: [createVariable()],
        page: {
          totalItems: 1,
          startCursor: null,
          endCursor: null,
          hasMoreTotalItems: false,
        },
      });
      mockSearchVariables().withSuccess({
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

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: /^select element$/i}));

      expect(
        await screen.findByText(
          'To view the variables, select a single element instance in the instance history.',
        ),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    },
  );

  it.each([true, false])(
    'should show failed placeholder if server error occurs while fetching variables - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      mockSearchVariables().withSuccess({
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

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchElementInstance('2').withSuccess(selectedElementInstance);
      mockSearchVariables().withServerError();

      await user.click(
        screen.getByRole('button', {name: /^select element instance$/i}),
      );

      expect(
        await screen.findByText('Variables could not be fetched'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    },
  );

  it.each([true, false])(
    'should show failed placeholder if network error occurs while fetching variables - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      mockSearchVariables().withSuccess({
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

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchElementInstance('2').withSuccess(selectedElementInstance);
      mockSearchVariables().withNetworkError();

      await user.click(
        screen.getByRole('button', {name: /^select element instance$/i}),
      );

      expect(
        await screen.findByText('Variables could not be fetched'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    },
  );

  it.each([true, false])(
    'should show failed placeholder if network error occurs while fetching selected element instance metadata - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      mockSearchVariables().withSuccess({
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

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchElementInstance('2').withNetworkError();
      mockSearchVariables().withSuccess({
        items: [createVariable()],
        page: {
          totalItems: 1,
          startCursor: null,
          endCursor: null,
          hasMoreTotalItems: false,
        },
      });

      await user.click(
        screen.getByRole('button', {name: /^select element instance$/i}),
      );

      expect(
        await screen.findByText('Variables could not be fetched'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    },
  );
});
