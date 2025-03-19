/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRef} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {open} from 'modules/mocks/diagrams';
import {
  Wrapper,
  adHocSubProcessesInstance,
  adHocNodeFlowNodeInstances,
} from './mocks';
import {FlowNodeInstancesTree} from '..';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('FlowNodeInstancesTree - Ad Hoc Sub Process', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(adHocSubProcessesInstance);
    mockFetchProcessXML().withSuccess(open('AdHocProcess.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      adHocSubProcessesInstance.bpmnProcessId,
    );

    processInstanceDetailsStore.init({id: adHocSubProcessesInstance.id});
    flowNodeInstanceStore.init();

    mockFetchFlowNodeInstances().withSuccess(adHocNodeFlowNodeInstances.level1);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });
  });

  it('should be able to unfold and fold ad hoc sub processes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={{
          id: adHocSubProcessesInstance.id,
          type: 'PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'AdHocSubProcess',
          treePath: adHocSubProcessesInstance.id,
          startDate: '',
          endDate: null,
          sortValues: [],
        }}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
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

    mockFetchFlowNodeInstances().withSuccess(adHocNodeFlowNodeInstances.level2);

    await user.type(
      screen.getByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(
      await screen.findByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Task_A')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Ad Hoc Sub Process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('Task A')).not.toBeInTheDocument();
  });
});
