/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor} from '@testing-library/react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {open} from 'modules/mocks/diagrams';
import {renderPopover} from './mocks';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';
import {act} from 'react';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {cancelAllTokens} from 'modules/utils/modifications';

describe('Modification Dropdown - Multi Scopes', () => {
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

  beforeEach(() => {
    mockFetchProcessXML().withSuccess(open('multipleInstanceSubProcess.bpmn'));
    mockFetchProcessDefinitionXml().withSuccess(
      open('multipleInstanceSubProcess.bpmn'),
    );
    mockFetchFlownodeInstancesStatistics().withSuccess(stats);
    mockFetchFlownodeInstancesStatistics().withSuccess(stats);
    modificationsStore.enableModificationMode();
  });

  afterEach(async () => {
    await new Promise(process.nextTick);
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

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'TaskB',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
  });

  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it.skip : it)(
    'should not support add modification for task with multiple inner parent scopes',
    async () => {
      renderPopover();

      await waitFor(() =>
        expect(
          processInstanceDetailsDiagramStore.state.diagramModel,
        ).not.toBeNull(),
      );

      act(() => {
        flowNodeSelectionStore.selectFlowNode({
          flowNodeId: 'TaskB',
        });
      });

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(screen.getByText(/Cancel/)).toBeInTheDocument();
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

      await waitFor(() =>
        expect(
          processInstanceDetailsDiagramStore.state.diagramModel,
        ).not.toBeNull(),
      );

      act(() => {
        flowNodeSelectionStore.selectFlowNode({
          flowNodeId: 'TaskB',
        });
      });

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(screen.getByText(/Cancel/)).toBeInTheDocument();
      expect(screen.getByText(/Move/)).toBeInTheDocument();
      expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    },
  );

  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it.skip : it)(
    'should render no modifications available',
    async () => {
      renderPopover();

      await waitFor(() =>
        expect(
          processInstanceDetailsDiagramStore.state.diagramModel,
        ).not.toBeNull(),
      );

      act(() => {
        cancelAllTokens('TaskB', 0, 0, {});
        flowNodeSelectionStore.selectFlowNode({
          flowNodeId: 'TaskB',
        });
      });

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

      await waitFor(() =>
        expect(
          processInstanceDetailsDiagramStore.state.diagramModel,
        ).not.toBeNull(),
      );

      act(() => {
        cancelAllTokens('TaskB', 0, 0, {});
        flowNodeSelectionStore.selectFlowNode({
          flowNodeId: 'TaskB',
        });
      });

      expect(
        await screen.findByText(/Flow Node Modifications/),
      ).toBeInTheDocument();
      expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
      expect(screen.getByText(/Add/)).toBeInTheDocument();
    },
  );
});
