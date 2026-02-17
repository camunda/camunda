/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {notificationsStore} from 'modules/stores/notifications';
import {adHocSubProcessInnerInstance, searchResult} from 'modules/testUtils';
import {ElementInstancesTree} from './index';
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
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';
import {
  endpoints,
  queryElementInstancesRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.8';

const diagramModel = await parseDiagramXML(adHocSubProcessInnerInstance);
const businessObjects = businessObjectsParser({diagramModel});

describe('ElementInstancesTree - Ad Hoc Sub Process Inner Instance', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      mockAdHocSubProcessInnerInstanceProcessInstance,
    );
    mockFetchProcessDefinitionXml().withSuccess(adHocSubProcessInnerInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess(searchResult([]));
    mockSearchElementInstances().withSuccess(
      adHocSubProcessInnerInstanceElementInstances.level1,
    );
    mockFetchElementInstance('inner-1').withSuccess(
      adHocSubProcessInnerInstanceElementInstances.level1.items[1]!,
    );
  });

  afterEach(() => {
    notificationsStore.reset();
  });

  it('should select inner instance with first child as anchor when node is expanded and has children', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

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
    // The right arrow press triggers a node selection on JSDOM so we need to reset the selection. This doesn't happeng in the browser
    await user.click(screen.getByText('Ad Hoc Inner Subprocess Test'));

    await user.click(
      await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
        selector: "[aria-expanded='true']",
      }),
    );

    await waitFor(() => {
      expect(screen.getByTestId('search')).toHaveTextContent(
        '?elementId=ad_hoc_subprocess&elementInstanceKey=inner-1&anchorElementId=user_task_in_ad_hoc_subprocess',
      );
    });
  });

  it('should fetch first child and select with anchor when clicking collapsed inner instance', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    mockServer.use(
      http.post(
        endpoints.queryElementInstances.getUrl(),
        async ({request}) => {
          const body = await request.json();
          const result = queryElementInstancesRequestBodySchema.safeParse(body);

          if (
            !result.success ||
            result.data?.filter?.elementInstanceScopeKey !== 'inner-1'
          ) {
            return HttpResponse.json(
              {
                error:
                  'Invalid payload: elementInstanceScopeKey must be in filter',
              },
              {status: 400},
            );
          }

          return HttpResponse.json(
            adHocSubProcessInnerInstanceElementInstances.level2,
          );
        },
        {once: true},
      ),
    );
    await user.click(screen.getByText('Ad Hoc Inner Subprocess Test'));

    await user.click(
      await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
        selector: "[aria-expanded='false']",
      }),
    );

    await waitFor(() => {
      expect(screen.getByTestId('search')).toHaveTextContent(
        '?elementId=ad_hoc_subprocess&elementInstanceKey=inner-1&anchorElementId=user_task_in_ad_hoc_subprocess',
      );
    });

    expect(notificationsStore.notifications).toEqual([]);
  });

  it('should display warning notification when inner instance has no children', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );
    const originalSearch = screen.getByTestId('search').textContent;

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(searchResult([]));

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

    expect(screen.getByTestId('search')).toHaveTextContent(originalSearch);
  });

  it('should display warning notification when fetching first child fails', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );
    const originalSearch = screen.getByTestId('search').textContent;

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    mockSearchElementInstances().withNetworkError();

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

    expect(screen.getByTestId('search')).toHaveTextContent(originalSearch);
  });
});
