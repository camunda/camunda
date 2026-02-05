/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from '@testing-library/react';
import {modificationsStore} from 'modules/stores/modifications';
import {open} from 'modules/mocks/diagrams';
import {renderPopover} from './mocks';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';
import {act} from 'react';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {cancelAllTokens} from 'modules/utils/modifications';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

const stats = {
  items: [
    {
      elementId: 'OuterSubProcess',
      active: 1,
      incidents: 0,
      completed: 0,
      canceled: 0,
    },
    {
      elementId: 'InnerSubProcess',
      active: 10,
      incidents: 0,
      completed: 0,
      canceled: 0,
    },
    {
      elementId: 'TaskB',
      active: 1,
      incidents: 0,
      completed: 0,
      canceled: 0,
    },
  ],
};

describe.skip('Modification Dropdown - Multi Scopes', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class ResizeObserver {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();

        constructor(callback: ResizeObserverCallback) {
          setTimeout(() => {
            try {
              callback([], this);
            } catch {
              // Ignore errors in mock
            }
          }, 0);
        }
      },
    );
    vi.stubGlobal(
      'SVGElement',
      class MockSVGElement extends Element {
        getBBox = vi.fn(() => ({
          x: 0,
          y: 0,
          width: 100,
          height: 100,
        }));
      },
    );
    const mockProcessInstance: ProcessInstance = {
      processInstanceKey: 'instance_id',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
    };

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(
      open('multipleInstanceSubProcess.bpmn'),
    );
    mockFetchFlownodeInstancesStatistics().withSuccess(stats);
    mockFetchFlownodeInstancesStatistics().withSuccess(stats);
    modificationsStore.enableModificationMode();
  });

  it('should support add modification for task with multiple scopes', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'OuterSubProcess',
          active: 1,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
        {
          elementId: 'InnerSubProcess',
          active: 1,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
        {
          elementId: 'TaskB',
          active: 10,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
      ],
    });

    renderPopover();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'TaskB',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
  });

  /* eslint-disable vitest/no-standalone-expect -- eslint doesn't understand dynamically skipped tests */
  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it.skip : it)(
    'should not support add modification for task with multiple inner parent scopes',
    async () => {
      renderPopover();

      act(() => {
        selectFlowNode(
          {},
          {
            flowNodeId: 'TaskB',
          },
        );
      });

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
      expect(screen.getByText(/Move/)).toBeInTheDocument();
      expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    },
  );

  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it.skip : it)(
    'should not support add modification for task with multiple outer parent scopes',
    async () => {
      mockFetchFlownodeInstancesStatistics().withSuccess({
        items: [
          {
            elementId: 'OuterSubProcess',
            active: 10,
            incidents: 0,
            completed: 0,
            canceled: 0,
          },
          {
            elementId: 'InnerSubProcess',
            active: 1,
            incidents: 0,
            completed: 0,
            canceled: 0,
          },
          {
            elementId: 'TaskB',
            active: 1,
            incidents: 0,
            completed: 0,
            canceled: 0,
          },
        ],
      });

      renderPopover();

      act(() => {
        selectFlowNode(
          {},
          {
            flowNodeId: 'TaskB',
          },
        );
      });

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
      expect(screen.getByText(/Move/)).toBeInTheDocument();
      expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    },
  );

  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it.skip : it)(
    'should render no modifications available',
    async () => {
      renderPopover();

      act(() => cancelAllTokens('TaskB', 0, 0, {}));

      act(() =>
        selectFlowNode(
          {},
          {
            flowNodeId: 'TaskB',
          },
        ),
      );

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/No modifications available/),
      ).toBeInTheDocument();
      expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    },
  );

  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it : it.skip)(
    'should render add modification for flow nodes that has multiple running scopes',
    async () => {
      renderPopover();

      act(() => cancelAllTokens('TaskB', 0, 0, {}));

      act(() =>
        selectFlowNode(
          {},
          {
            flowNodeId: 'TaskB',
          },
        ),
      );

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
      expect(screen.getByText(/Add/)).toBeInTheDocument();
    },
  );
  /* eslint-enable vitest/no-standalone-expect */
});
