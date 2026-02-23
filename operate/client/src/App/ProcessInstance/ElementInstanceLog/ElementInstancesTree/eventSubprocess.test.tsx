/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ElementInstancesTree} from './index';
import {
  eventSubProcessElementInstances,
  Wrapper,
  mockEventSubprocessInstance,
} from './mocks';
import {eventSubProcess, searchResult} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {businessObjectsParser} from 'modules/queries/processDefinitions/useBusinessObjects';

const diagramModel = await parseDiagramXML(eventSubProcess);
const businessObjects = businessObjectsParser({diagramModel});

describe('ElementInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(mockEventSubprocessInstance);
    mockFetchProcessDefinitionXml().withSuccess(eventSubProcess);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess(searchResult([]));
    mockSearchElementInstances().withSuccess(
      eventSubProcessElementInstances.level1,
    );
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockEventSubprocessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      eventSubProcessElementInstances.level2,
    );
    mockFetchElementInstance(':id').withSuccess(
      eventSubProcessElementInstances.level1.items[2]!,
    );

    await user.type(
      await screen.findByLabelText('Event Subprocess', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Interrupting timer')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();
  });
});
