/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
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
            elementId: 'TEST_FLOW_NODE',
          })
        }
      >
        select flow node
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'TEST_FLOW_NODE',
            elementInstanceKey: '2',
          })
        }
      >
        select flow node instance
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
  elementId: 'TEST_FLOW_NODE',
  elementName: 'Test Flow Node',
  type: 'SERVICE_TASK' as const,
  state: 'ACTIVE' as const,
  startDate: '2018-06-21',
  processDefinitionId: 'someKey',
  processInstanceKey: '1',
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

describe('VariablePanel placeholders', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
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

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
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
        page: {totalItems: 2},
      });
      mockSearchVariables().withSuccess({
        items: [createVariable()],
        page: {
          totalItems: 1,
        },
      });
      mockSearchVariables().withSuccess({
        items: [createVariable()],
        page: {
          totalItems: 1,
        },
      });

      const {user} = render(
        <VariablePanel setListenerTabVisibility={vi.fn()} />,
        {
          wrapper: getWrapper(),
        },
      );

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      await user.click(
        screen.getByRole('button', {name: /^select flow node$/i}),
      );

      expect(
        await screen.findByText(
          'To view the Variables, select a single Flow Node Instance in the Instance History.',
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
        },
      });

      const {user} = render(
        <VariablePanel setListenerTabVisibility={vi.fn()} />,
        {
          wrapper: getWrapper(),
        },
      );

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchElementInstance('2').withSuccess(selectedElementInstance);
      mockSearchVariables().withServerError();

      await user.click(
        screen.getByRole('button', {name: /^select flow node instance$/i}),
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
        },
      });

      const {user} = render(
        <VariablePanel setListenerTabVisibility={vi.fn()} />,
        {
          wrapper: getWrapper(),
        },
      );

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchElementInstance('2').withSuccess(selectedElementInstance);
      mockSearchVariables().withNetworkError();

      await user.click(
        screen.getByRole('button', {name: /^select flow node instance$/i}),
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
    'should show failed placeholder if network error occurs while fetching selected flow node instance metadata - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      mockSearchVariables().withSuccess({
        items: [createVariable()],
        page: {
          totalItems: 1,
        },
      });

      const {user} = render(
        <VariablePanel setListenerTabVisibility={vi.fn()} />,
        {
          wrapper: getWrapper(),
        },
      );

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchElementInstance('2').withNetworkError();
      mockSearchVariables().withSuccess({
        items: [createVariable()],
        page: {
          totalItems: 1,
        },
      });

      await user.click(
        screen.getByRole('button', {name: /^select flow node instance$/i}),
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
