/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {notificationsStore} from 'modules/stores/notifications';
import {adHocSubProcessInnerInstance} from 'modules/testUtils';
import {ElementInstancesTree} from './index';
import {useLocation} from 'react-router-dom';
import {
  Wrapper,
  mockAdHocSubProcessInnerInstanceProcessInstance,
  adHocSubProcessInnerInstanceElementInstances,
} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {businessObjectsParser} from 'modules/queries/processDefinitions/useBusinessObjects';

const diagramModel = await parseDiagramXML(adHocSubProcessInnerInstance);
const businessObjects = businessObjectsParser({diagramModel});

describe('ElementInstancesTree - Ad Hoc Sub Process Inner Instance', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      mockAdHocSubProcessInnerInstanceProcessInstance,
    );
    mockFetchProcessDefinitionXml().withSuccess(adHocSubProcessInnerInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchElementInstances().withSuccess(
      adHocSubProcessInnerInstanceElementInstances.level1,
    );
    mockFetchElementInstance('inner-1').withSuccess({
      elementInstanceKey: 'inner-1',
      elementId: 'ad_hoc_subprocess',
      elementName: 'Ad Hoc Sub Process Inner Instance',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionId: 'AdHocProcess',
      processInstanceKey: '222734982389310',
      processDefinitionKey: '12517992348923884',
      hasIncident: false,
      tenantId: '<default>',
    });
  });

  afterEach(() => {
    notificationsStore.reset();
  });

  it('should select inner instance with first child as anchor when node is expanded and has children', async () => {
    let location: ReturnType<typeof useLocation>;

    const TestComponent = () => {
      location = useLocation();
      return (
        <ElementInstancesTree
          processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
          businessObjects={businessObjects}
        />
      );
    };

    const {user} = render(<TestComponent />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      adHocSubProcessInnerInstanceElementInstances.level2,
    );
    mockSearchElementInstances().withSuccess(
      adHocSubProcessInnerInstanceElementInstances.level2,
    );

    await user.type(
      await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    // The right arrow press triggers a node selection on JSDOM so we need to reset the selection. This doesn't happen in the browser
    await user.click(screen.getByText('Ad Hoc Inner Subprocess Test'));

    await user.click(
      await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
        selector: "[aria-expanded='true']",
      }),
    );

    await waitFor(() => {
      const searchParams = new URLSearchParams(location!.search);
      expect(searchParams.get('elementId')).toBe('ad_hoc_subprocess');
      expect(searchParams.get('elementInstanceKey')).toBe('inner-1');
      expect(searchParams.get('anchorElementId')).toBe(
        'user_task_in_ad_hoc_subprocess',
      );
    });
  });

  it('should fetch first child and select with anchor when clicking collapsed inner instance', async () => {
    let location: ReturnType<typeof useLocation>;

    const TestComponent = () => {
      location = useLocation();
      return (
        <ElementInstancesTree
          processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
          businessObjects={businessObjects}
        />
      );
    };

    const {user} = render(<TestComponent />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      adHocSubProcessInnerInstanceElementInstances.level2,
    );
    await user.click(screen.getByText('Ad Hoc Inner Subprocess Test'));

    await user.click(
      await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
        selector: "[aria-expanded='false']",
      }),
    );

    await waitFor(() => {
      const searchParams = new URLSearchParams(location!.search);
      expect(searchParams.get('elementId')).toBe('ad_hoc_subprocess');
      expect(searchParams.get('elementInstanceKey')).toBe('inner-1');
      expect(searchParams.get('anchorElementId')).toBe(
        'user_task_in_ad_hoc_subprocess',
      );
    });
  });

  it('should display warning notification when inner instance has no children', async () => {
    let location: ReturnType<typeof useLocation>;

    const TestComponent = () => {
      location = useLocation();
      return (
        <ElementInstancesTree
          processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
          businessObjects={businessObjects}
        />
      );
    };

    const {user} = render(<TestComponent />, {
      wrapper: Wrapper,
    });

    const originalSearch = location!.search;

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      adHocSubProcessInnerInstanceElementInstances.emptyLevel,
    );

    await user.click(
      await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
        selector: "[aria-expanded='false']",
      }),
    );

    await waitFor(() => {
      expect(notificationsStore.notifications).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            kind: 'warning',
            title:
              'No child instances found for Ad Hoc Sub Process Inner Instance',
          }),
        ]),
      );
    });

    expect(location!.search).toEqual(originalSearch);
  });
});
