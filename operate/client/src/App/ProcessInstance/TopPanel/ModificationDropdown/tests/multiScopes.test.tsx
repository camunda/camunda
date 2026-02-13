/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitForElementToBeRemoved} from '@testing-library/react';
import {modificationsStore} from 'modules/stores/modifications';
import {open} from 'modules/mocks/diagrams';
import {renderPopover} from './mocks';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {Paths} from 'modules/Routes';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';

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

describe('Modification Dropdown - Multi Scopes', () => {
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
    mockSearchElementInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
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

    renderPopover([`${Paths.processInstance('instance_id')}?elementId=TaskB`]);

    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
  });

  it('should render no modifications available when multi sub process instance is selected', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'OuterSubProcess',
          active: 1,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
      ],
    });

    mockFetchElementInstance('2251799813686156').withSuccess({
      elementInstanceKey: '2251799813686156',
      processInstanceKey: '1',
      processDefinitionKey: 'processName',
      processDefinitionId: 'processName',
      state: 'ACTIVE' as const,
      type: 'SUB_PROCESS' as const,
      elementId: 'OuterSubProcess',
      elementName: 'Outer Sub Process',
      hasIncident: false,
      tenantId: '<default>',
      startDate: '2018-12-12T00:00:02.000+0000',
      endDate: '2018-12-12T00:00:03.000+0000',
    });

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=OuterSubProcess&elementInstanceKey=2251799813686156`,
    ]);

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    await waitForElementToBeRemoved(() => screen.queryByTitle(/loading/i));

    expect(screen.getByText(/No modifications available/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });
});
