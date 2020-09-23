/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {createMemoryHistory} from 'history';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {ThemeProvider} from 'modules/theme';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {EXPAND_STATE} from 'modules/constants';

import {
  flushPromises,
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';

import {filters} from 'modules/stores/filters';

import {
  mockPropsWithEmptyInstances,
  mockPropsWithInstances,
  mockPropsWithNoOperation,
  mockPropsBeforeDataLoaded,
  mockPropsWithPoll,
  ACTIVE_INSTANCE,
} from './index.setup';

import ListPanel from './index';
import {MemoryRouter} from 'react-router-dom';
import PropTypes from 'prop-types';

import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {instances} from 'modules/stores/instances';

jest.mock('modules/utils/bpmn');

describe('ListPanel', () => {
  const locationMock = {pathname: '/instances'};
  const historyMock = createMemoryHistory();

  const Wrapper = ({children}) => {
    return (
      <MemoryRouter>
        <ThemeProvider>
          <DataManagerProvider>
            <CollapsablePanelProvider>
              <InstancesPollProvider>{children}</InstancesPollProvider>
            </CollapsablePanelProvider>
          </DataManagerProvider>
        </ThemeProvider>
      </MemoryRouter>
    );
  };
  Wrapper.propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node,
    ]),
  };

  beforeEach(async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );
    filters.setUrlParameters(historyMock, locationMock);

    await instances.fetchInstances({});
    await filters.init();
    filters.setFilter(DEFAULT_FILTER);
    filters.setSorting(DEFAULT_SORTING);
    filters.setEntriesPerPage(10);
    createMockDataManager();
  });

  afterEach(() => {
    jest.clearAllMocks();
    filters.reset();
  });

  describe('messages', () => {
    it('should display a message for empty list when filter has no state', async () => {
      filters.setFilter({});
      render(<ListPanel {...mockPropsWithEmptyInstances} />);
      expect(
        screen.getByText('There are no instances matching this filter set.')
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          'To see some results, select at least one instance state.'
        )
      ).toBeInTheDocument();
    });

    it('should display an empty list message when filter has at least one state', async () => {
      filters.setFilter(DEFAULT_FILTER);
      render(<ListPanel {...mockPropsWithEmptyInstances} />);
      expect(
        screen.getByText('There are no instances matching this filter set.')
      ).toBeInTheDocument();
      expect(
        screen.queryByText(
          'To see some results, select at least one instance state.'
        )
      ).not.toBeInTheDocument();
    });
  });

  describe('display instances List', () => {
    it('should render a skeleton', () => {
      const listPanelComponent = render(
        <ListPanel {...mockPropsBeforeDataLoaded} />
      );

      expect(screen.getByTestId('listpanel-skeleton')).toBeInTheDocument();
      listPanelComponent.rerender(
        <ListPanel {...mockPropsWithEmptyInstances} />
      );
      expect(
        screen.queryByTestId('listpanel-skeleton')
      ).not.toBeInTheDocument();
    });

    it('should render table body and footer', () => {
      render(<ListPanel {...mockPropsWithInstances} />, {wrapper: Wrapper});
      expect(screen.getByTestId('instances-list')).toBeInTheDocument();
      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
    });

    it('should render Footer when list is empty', () => {
      render(<ListPanel {...mockPropsWithEmptyInstances} />);

      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
    });
  });

  describe('polling for instances changes', () => {
    it('should not send ids for polling if no instances with active operations are displayed', async () => {
      render(<ListPanel.WrappedComponent {...mockPropsWithNoOperation} />, {
        wrapper: Wrapper,
      });

      expect(mockPropsWithPoll.polling.addIds).not.toHaveBeenCalled();
    });

    it('should send ids for polling if at least one instance with active operations is diplayed', async () => {
      const {rerender} = render(
        <ListPanel.WrappedComponent
          {...mockPropsWithPoll}
          instances={[ACTIVE_INSTANCE]}
        />,
        {wrapper: Wrapper}
      );

      filters.setFilter(DEFAULT_FILTER);
      filters.setSorting(DEFAULT_SORTING);
      filters.setEntriesPerPage(2);

      // simulate change of instances displayed

      rerender(
        <ListPanel.WrappedComponent
          {...mockPropsWithPoll}
          instances={[ACTIVE_INSTANCE]}
          isLoading={true}
        />,
        {wrapper: Wrapper}
      );

      rerender(
        <ListPanel.WrappedComponent
          {...mockPropsWithPoll}
          instances={[ACTIVE_INSTANCE]}
          isLoading={false}
        />,
        {wrapper: Wrapper}
      );

      // then
      expect(mockPropsWithPoll.polling.addIds).toHaveBeenCalledWith([
        ACTIVE_INSTANCE.id,
      ]);
    });

    it('should add the id to InstancesPollProvider after user starts operation on instance from list', async () => {
      render(<ListPanel.WrappedComponent {...mockPropsWithPoll} />, {
        wrapper: Wrapper,
      });

      fireEvent.click(screen.getByRole('button', {name: 'Cancel Instance 1'}));

      expect(mockPropsWithPoll.polling.addIds).toHaveBeenCalledWith(['1']);
    });

    // this feature does not work (did not work before either, the test was wrong)
    it.skip('should not poll for instances with active operations that are no longer in view after collapsing', async () => {
      const {rerender} = render(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} />,
        {wrapper: Wrapper}
      );

      // simulate set number of visible rows from List
      filters.setEntriesPerPage(2);

      rerender(
        <ListPanel.WrappedComponent
          {...mockPropsWithPoll}
          instances={[ACTIVE_INSTANCE]}
          isLoading={true}
        />,
        {wrapper: Wrapper}
      );

      rerender(
        <ListPanel.WrappedComponent
          {...mockPropsWithPoll}
          instances={[ACTIVE_INSTANCE]}
          isLoading={false}
        />,
        {wrapper: Wrapper}
      );

      expect(mockPropsWithPoll.polling.addIds).not.toHaveBeenCalled();
    });

    // https://app.camunda.com/jira/browse/OPE-395
    // skipped because it tests commented code
    it.skip('should refetch instances when expanding the list panel', async () => {
      //  render(
      //     <ListPanel.WrappedComponent {...mockPropsWithPoll} {...{dataManager}} />
      //   );
      //   filters.setFilter(DEFAULT_FILTER);
      //   filters.setSorting(DEFAULT_SORTING);
      //   // when
      //   // simulate set number of visible rows from List
      //   filters.setEntriesPerPage(2);
      //   // simulate load of instances in list
      //   node.setProps({isLoading: true});
      //   node.setProps({isLoading: false});
      //   filters.setEntriesPerPage(3);
      //   await flushPromises();
      //   node.update();
      //   expect(dataManager.update).toHaveBeenCalledTimes(1);
    });
  });
});
