/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {multiInstanceProcess, searchResult} from 'modules/testUtils';
import {ElementInstancesTree} from './index';
import {Wrapper, mockMultiInstanceProcessInstance} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {businessObjectsParser} from 'modules/queries/processDefinitions/useBusinessObjects';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';

const diagramModel = await parseDiagramXML(multiInstanceProcess);
const businessObjects = businessObjectsParser({diagramModel});

const MULTI_INSTANCE_BODY_ELEMENT: ElementInstance = {
  elementInstanceKey: '2251799813686156',
  processInstanceKey: '2251799813686118',
  processDefinitionKey: '2251799813686038',
  processDefinitionId: 'multiInstanceProcess',
  state: 'ACTIVE',
  type: 'MULTI_INSTANCE_BODY',
  elementId: 'filterMapSubProcess',
  elementName: 'Filter-Map Sub Process',
  hasIncident: true,
  tenantId: '<default>',
  startDate: '2020-08-18T12:07:34.205+0000',
};

const SUB_PROCESS_ELEMENT: ElementInstance = {
  elementInstanceKey: '2251799813686166',
  processInstanceKey: '2251799813686118',
  processDefinitionKey: '2251799813686038',
  processDefinitionId: 'multiInstanceProcess',
  state: 'ACTIVE',
  type: 'SUB_PROCESS',
  elementId: 'filterMapSubProcess',
  elementName: 'Filter-Map Sub Process',
  hasIncident: true,
  tenantId: '<default>',
  startDate: '2020-08-18T12:07:34.281+0000',
};

describe('ElementInstancesTree - Multi Instance Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

    mockQueryBatchOperationItems().withSuccess(searchResult([]));

    mockSearchElementInstances().withSuccess(
      searchResult([
        MULTI_INSTANCE_BODY_ELEMENT,
        {
          elementInstanceKey: '2251799813686130',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'COMPLETED',
          type: 'PARALLEL_GATEWAY',
          elementId: 'peterFork',
          elementName: 'Peter Fork',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
        },
      ]),
    );
  });

  it('should load the instance history', async () => {
    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });

    render(
      <ElementInstancesTree
        processInstance={mockMultiInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();
    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)'),
    ).toBeInTheDocument();
  });

  it('should be able to unfold and fold subprocesses', async () => {
    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });

    const {user} = render(
      <ElementInstancesTree
        processInstance={mockMultiInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='false']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      searchResult([SUB_PROCESS_ELEMENT]),
    );
    mockFetchElementInstance(
      MULTI_INSTANCE_BODY_ELEMENT.elementInstanceKey,
    ).withSuccess(MULTI_INSTANCE_BODY_ELEMENT);

    await user.type(
      await screen.findByLabelText('Filter-Map Sub Process (Multi Instance)', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Filter-Map Sub Process (Multi Instance)', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      searchResult([
        {
          elementInstanceKey: '2251799813686204',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'COMPLETED',
          type: 'START_EVENT',
          elementId: 'startFilterMap',
          elementName: 'Start Filter-Map',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:34.337+0000',
          endDate: '2020-08-18T12:07:34.445+0000',
        },
      ]),
    );
    mockFetchElementInstance(
      SUB_PROCESS_ELEMENT.elementInstanceKey,
    ).withSuccess(SUB_PROCESS_ELEMENT);

    await user.type(
      await screen.findByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowRight}',
    );

    expect(await screen.findByText('Start Filter-Map')).toBeInTheDocument();
    expect(
      screen.getByLabelText('Filter-Map Sub Process (Multi Instance)', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Filter-Map Sub Process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();
  });

  it('should poll for instances on root level', async () => {
    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });

    vi.useFakeTimers({shouldAdvanceTime: true});

    render(
      <ElementInstancesTree
        processInstance={mockMultiInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();

    const withinMultiInstanceElementInstance = within(
      await screen.findByTestId(`tree-node-2251799813686156`),
    );

    expect(
      await withinMultiInstanceElementInstance.findByTestId('INCIDENT-icon'),
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceElementInstance.queryByTestId('COMPLETED-icon'),
    ).not.toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);

    mockSearchElementInstances().withSuccess(
      searchResult([
        {
          elementInstanceKey: '2251799813686130',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'COMPLETED',
          type: 'PARALLEL_GATEWAY',
          elementId: 'peterFork',
          elementName: 'Peter Fork',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
        },
        {
          elementInstanceKey: '2251799813686156',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'COMPLETED',
          type: 'MULTI_INSTANCE_BODY',
          elementId: 'filterMapSubProcess',
          elementName: 'Filter-Map Sub Process',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:34.205+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
        },
      ]),
    );

    vi.runOnlyPendingTimers();

    expect(
      await withinMultiInstanceElementInstance.findByTestId('COMPLETED-icon'),
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceElementInstance.queryByTestId('INCIDENT-icon'),
    ).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
