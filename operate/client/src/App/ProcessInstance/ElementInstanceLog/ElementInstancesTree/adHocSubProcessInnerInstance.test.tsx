/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {notificationsStore} from 'modules/stores/notifications';
import {instanceHistorySortOrderStore} from 'modules/stores/instanceHistorySortOrder';
import {adHocSubProcessInnerInstance, searchResult} from 'modules/testUtils';
import {ElementInstancesTree} from './index';
import {
  getWrapper,
  mockAdHocSubProcessInnerInstanceProcessInstance,
  adHocSubProcessInnerInstanceElementInstances,
  parseBusinessObjects,
} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';
import {
  endpoints,
  queryElementInstancesRequestBodySchema,
  type ElementInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';

describe('ElementInstancesTree - Ad Hoc Sub Process Inner Instance', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      mockAdHocSubProcessInnerInstanceProcessInstance,
    );
    mockFetchProcessDefinitionXml().withSuccess(adHocSubProcessInnerInstance);
    mockFetchElementInstancesStatistics().withSuccess({items: []});
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
    const {businessObjects} = await parseBusinessObjects(
      adHocSubProcessInnerInstance,
    );
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByText('Ad Hoc Inner Subprocess Test'),
    ).toBeInTheDocument();

    // Two one-time handlers needed: JSDOM fires an implicit node selection on the arrow-key
    // press (triggering a child fetch), then the manual reset-click triggers another fetch.
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
    const {businessObjects} = await parseBusinessObjects(
      adHocSubProcessInnerInstance,
    );
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: getWrapper(),
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
    const {businessObjects} = await parseBusinessObjects(
      adHocSubProcessInnerInstance,
    );
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: getWrapper(),
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
    const {businessObjects} = await parseBusinessObjects(
      adHocSubProcessInnerInstance,
    );
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: getWrapper(),
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

  describe('anchor selection follows the displayed sort order', () => {
    // Clicking the inner instance row anchors on its first child *as displayed*,
    // so two children with distinct element ids are needed to tell the orders
    // apart - the level2 fixture is the earlier one, the second is the later.
    const [earliestChild] =
      adHocSubProcessInnerInstanceElementInstances.level2.items;
    const innerChildren: ElementInstance[] = [
      earliestChild!,
      {
        ...earliestChild!,
        elementInstanceKey: 'inner-child-latest',
        type: 'SERVICE_TASK',
        elementId: 'service_task_in_ad_hoc_subprocess',
        elementName: 'Service Task',
        startDate: '2020-08-18T12:07:36.500+0000',
      },
    ];

    /**
     * Answers the inner-instance child query the way the server would: applies the
     * requested order and honours the page limit, so the assertion below only
     * passes when the request actually carried the displayed sort order.
     */
    const mockSortAwareChildQuery = (
      onSortRequested: (sort: unknown) => void,
    ) => {
      mockServer.use(
        http.post(
          endpoints.queryElementInstances.getUrl(),
          async ({request}) => {
            const body = await request.json();
            const result =
              queryElementInstancesRequestBodySchema.safeParse(body);

            if (
              !result.success ||
              result.data?.filter?.elementInstanceScopeKey !== 'inner-1'
            ) {
              return HttpResponse.json(
                {error: 'Unexpected request'},
                {status: 400},
              );
            }

            onSortRequested(result.data.sort);

            const isDescending = result.data.sort?.[0]?.order === 'desc';
            const sorted = isDescending
              ? [...innerChildren].reverse()
              : innerChildren;

            return HttpResponse.json({
              items: sorted.slice(0, result.data.page?.limit ?? sorted.length),
              page: {
                totalItems: innerChildren.length,
                startCursor: null,
                endCursor: null,
                hasMoreTotalItems: false,
              },
            });
          },
          {once: true},
        ),
      );
    };

    const clickCollapsedInnerInstance = async (
      onSortRequested: (sort: unknown) => void,
    ) => {
      const {businessObjects} = await parseBusinessObjects(
        adHocSubProcessInnerInstance,
      );
      const {user} = render(
        <ElementInstancesTree
          processInstance={mockAdHocSubProcessInnerInstanceProcessInstance}
          businessObjects={businessObjects}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      expect(
        await screen.findByText('Ad Hoc Inner Subprocess Test'),
      ).toBeInTheDocument();

      // Installed only once the root tree has loaded, so it answers the child
      // query rather than the root's own query.
      mockSortAwareChildQuery(onSortRequested);

      await user.click(screen.getByText('Ad Hoc Inner Subprocess Test'));

      await user.click(
        await screen.findByLabelText('Ad Hoc Sub Process Inner Instance', {
          selector: "[aria-expanded='false']",
        }),
      );
    };

    afterEach(() => {
      instanceHistorySortOrderStore.reset();
    });

    it('should anchor on the latest child when the history is latest-first', async () => {
      // given the default latest-first order
      expect(instanceHistorySortOrderStore.order).toBe('desc');

      let requestedSort: unknown;

      // when the collapsed inner instance is clicked
      await clickCollapsedInnerInstance((sort) => {
        requestedSort = sort;
      });

      // then the newest child anchors the selection
      await waitFor(() => {
        expect(screen.getByTestId('search')).toHaveTextContent(
          '?elementId=ad_hoc_subprocess&elementInstanceKey=inner-1&anchorElementId=service_task_in_ad_hoc_subprocess',
        );
      });

      expect(requestedSort).toEqual([
        {field: 'startDate', order: 'desc'},
        {field: 'elementInstanceKey', order: 'desc'},
      ]);
      expect(notificationsStore.notifications).toEqual([]);
    });

    it('should anchor on the earliest child when the history is oldest-first', async () => {
      // given the user switched the history back to oldest-first
      instanceHistorySortOrderStore.toggle();
      expect(instanceHistorySortOrderStore.order).toBe('asc');

      let requestedSort: unknown;

      // when the collapsed inner instance is clicked
      await clickCollapsedInnerInstance((sort) => {
        requestedSort = sort;
      });

      // then the oldest child anchors the selection instead
      await waitFor(() => {
        expect(screen.getByTestId('search')).toHaveTextContent(
          '?elementId=ad_hoc_subprocess&elementInstanceKey=inner-1&anchorElementId=user_task_in_ad_hoc_subprocess',
        );
      });

      expect(requestedSort).toEqual([
        {field: 'startDate', order: 'asc'},
        {field: 'elementInstanceKey', order: 'asc'},
      ]);
      expect(notificationsStore.notifications).toEqual([]);
    });
  });
});
