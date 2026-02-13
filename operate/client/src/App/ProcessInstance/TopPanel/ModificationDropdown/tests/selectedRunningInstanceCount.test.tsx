/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from 'modules/testing-library';
import {modificationsStore} from 'modules/stores/modifications';
import {renderPopover} from './mocks';
import {open} from 'modules/mocks/diagrams';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {Paths} from 'modules/Routes';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';

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

describe('selectedRunningInstanceCount', () => {
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
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'StartEvent_1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'service-task-1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'multi-instance-subprocess',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'subprocess-start-1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'subprocess-service-task',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'service-task-7',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          elementId: 'message-boundary',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
    mockSearchElementInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  it('should not render when there are no running instances selected', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    modificationsStore.enableModificationMode();

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=StartEvent_1`,
    ]);

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/Selected running instances/),
    ).not.toBeInTheDocument();
  });

  it('should render when there are running instances selected', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    modificationsStore.enableModificationMode();

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-7`,
    ]);

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(
      await screen.findByText(/Selected running instances/),
    ).toBeInTheDocument();
  });
});
