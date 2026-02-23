/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {open} from 'modules/mocks/diagrams';
import {searchResult} from 'modules/testUtils';
import {
  Wrapper,
  adHocNodeElementInstances,
  mockAdHocSubProcessesInstance,
} from './mocks';
import {ElementInstancesTree} from './index';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {businessObjectsParser} from 'modules/queries/processDefinitions/useBusinessObjects';

const adHocProcessXml = open('AdHocProcess.bpmn');
const diagramModel = await parseDiagramXML(adHocProcessXml);
const businessObjects = businessObjectsParser({diagramModel});

describe('ElementInstancesTree - Ad Hoc Sub Process', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(mockAdHocSubProcessesInstance);
    mockFetchProcessDefinitionXml().withSuccess(open('AdHocProcess.bpmn'));
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess(searchResult([]));
    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level1);
  });

  it('should be able to unfold and fold ad hoc sub processes', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessesInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Task A')).not.toBeInTheDocument();

    mockSearchElementInstances().withSuccess(adHocNodeElementInstances.level2);
    mockFetchElementInstance(':id').withSuccess(
      adHocNodeElementInstances.level1.items[1]!,
    );

    await user.type(
      await screen.findByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Task A')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Task A')).not.toBeInTheDocument();
  });
});
