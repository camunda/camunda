/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';

import {currentInstanceStore} from 'modules/stores/currentInstance';
import Header from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createMemoryHistory, History} from 'history';
import {render, within, fireEvent, screen} from '@testing-library/react';
import {location, mockCollapsablePanelProps} from './index.setup';
import {instancesStore} from 'modules/stores/instances';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

// Header component fetches user information in the background, which is an async action and might complete after the tests finished.
// Tests also depend on the statistics fetch to be completed, in order to test the results that are rendered in the screen.
// So we have to use this function in all tests, in order to verify all the async actions are completed, when we want them to be completed.
const waitForComponentToLoad = async () => {
  expect(await screen.findByText('firstname lastname')).toBeInTheDocument();
  expect(await screen.findByTitle('View 731 Incidents')).toBeInTheDocument();
};

type Props = {
  history?: History;
  location: {
    pathname: string;
  };
  isFiltersCollapsed: boolean;
  expandFilters: () => void;
};

const MockApp: React.FC<Props> = ({
  history = createMemoryHistory(),
  ...props
}) => (
  <ThemeProvider>
    <Router history={history}>
      <Header.WrappedComponent {...props} />
    </Router>
  </ThemeProvider>
);

describe('Header', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(
          ctx.json({
            firstname: 'firstname',
            lastname: 'lastname',
          })
        )
      ),
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 731,
          })
        )
      ),
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'first_instance_id',
            state: 'ACTIVE',
          })
        )
      )
    );
  });

  afterEach(() => {
    currentInstanceStore.reset();
    instancesStore.reset();
    statisticsStore.reset();
  });

  it('should render all header links', async () => {
    const mockProps = {
      location: location.dashboard,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);

    await waitForComponentToLoad();

    expect(screen.getByText('Camunda Operate')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Running Instances')).toBeInTheDocument();
    expect(screen.getByText('Filters')).toBeInTheDocument();
    expect(screen.getByText('Incidents')).toBeInTheDocument();
    expect(screen.queryByText('Instance')).toBeNull();
  });

  it('should render incident, filter and instances counts correctly', async () => {
    const mockProps = {
      location: location.dashboard,
      ...mockCollapsablePanelProps,
    };
    instancesStore.setInstances({
      filteredInstancesCount: 200,
      workflowInstances: [],
    });

    render(<MockApp {...mockProps} />);

    expect(
      within(screen.getByTestId('header-link-incidents')).getByTestId('badge')
    ).toBeEmptyDOMElement();
    expect(
      within(screen.getByTestId('header-link-filters')).getByTestId('badge')
    ).toBeEmptyDOMElement();
    expect(
      within(screen.getByTestId('header-link-instances')).getByTestId('badge')
    ).toBeEmptyDOMElement();

    await waitForComponentToLoad();

    expect(
      within(screen.getByTestId('header-link-incidents')).getByTestId('badge')
      // @ts-expect-error ts-migrate(2345) FIXME: Argument of type 'number' is not assignable to par... Remove this comment to see the full error message
    ).toHaveTextContent(731);
    expect(
      within(screen.getByTestId('header-link-filters')).getByTestId('badge')
      // @ts-expect-error ts-migrate(2345) FIXME: Argument of type 'number' is not assignable to par... Remove this comment to see the full error message
    ).toHaveTextContent(200);
    expect(
      within(screen.getByTestId('header-link-instances')).getByTestId('badge')
      // @ts-expect-error ts-migrate(2345) FIXME: Argument of type 'number' is not assignable to par... Remove this comment to see the full error message
    ).toHaveTextContent(821);
  });

  it('should render user element', async () => {
    const mockProps = {
      location: location.dashboard,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);

    await waitForComponentToLoad();
    expect(screen.getByText('firstname lastname')).toBeInTheDocument();
  });

  it('should highlight links correctly on dashboard page', async () => {
    const mockProps = {
      location: location.dashboard,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    expect(screen.getByText('Dashboard')).not.toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Running Instances')).toHaveStyleRule(
      'opacity',
      '0.5'
    );
    expect(screen.getByText('Filters')).toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Incidents')).toHaveStyleRule('opacity', '0.5');
  });

  it('should highlight links correctly on instances page', async () => {
    const mockProps = {
      location: location.instances,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    expect(screen.getByText('Dashboard')).toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Running Instances')).not.toHaveStyleRule(
      'opacity',
      '0.5'
    );
    expect(screen.getByText('Filters')).not.toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Incidents')).toHaveStyleRule('opacity', '0.5');
  });

  it('should render instance details skeleton on instance view', async () => {
    const mockProps = {
      location: location.instance,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    expect(screen.queryByTestId(/state-icon/)).not.toBeInTheDocument();
    expect(screen.getByTestId('instance-skeleton-circle')).toBeInTheDocument();
    expect(screen.getByTestId('instance-skeleton-block')).toBeInTheDocument();
  });

  it('should render instance details on instance view', async () => {
    const MOCK_INSTANCE_ID = 'first_instance_id';

    const mockProps = {
      location: location.instance,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    currentInstanceStore.init(MOCK_INSTANCE_ID);

    expect(
      await screen.findByText(`Instance ${MOCK_INSTANCE_ID}`)
    ).toBeInTheDocument();
    expect(screen.getByTestId(/state-icon/)).toBeInTheDocument();
    expect(
      screen.queryByTestId('instance-skeleton-circle')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('instance-skeleton-block')
    ).not.toBeInTheDocument();
  });

  it('should render instance details on refresh', async () => {
    const MOCK_FIRST_INSTANCE_ID = 'first_instance_id';
    const MOCK_SECOND_INSTANCE_ID = 'second_instance_id';

    const mockProps = {
      location: location.instance,
      ...mockCollapsablePanelProps,
    };
    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    jest.useFakeTimers();
    currentInstanceStore.init(MOCK_FIRST_INSTANCE_ID);
    expect(
      await screen.findByText(`Instance ${MOCK_FIRST_INSTANCE_ID}`)
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 732,
          })
        )
      ),
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'second_instance_id',
            state: 'ACTIVE',
          })
        )
      )
    );

    jest.runOnlyPendingTimers();

    expect(await screen.findByTitle('View 732 Incidents')).toBeInTheDocument();
    expect(
      await screen.findByText(`Instance ${MOCK_SECOND_INSTANCE_ID}`)
    ).toBeInTheDocument();
    expect(screen.queryByText(`Instance ${MOCK_FIRST_INSTANCE_ID}`)).toBeNull();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should go to the correct pages when clicking on header links', async () => {
    const MOCK_HISTORY = createMemoryHistory();

    const mockProps = {
      history: MOCK_HISTORY,
      location: location.instance,
      ...mockCollapsablePanelProps,
    };

    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    fireEvent.click(await screen.findByText('Camunda Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');

    fireEvent.click(await screen.findByText('Running Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%7D'
    );

    fireEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');

    fireEvent.click(await screen.findByText('Filters'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%7D'
    );

    fireEvent.click(await screen.findByText('Incidents'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?filter=%7B%22incidents%22%3Atrue%7D'
    );
  });

  it('should go to the correct pages when clicking on header links (with gse url)', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });

    const mockProps = {
      history: MOCK_HISTORY,
      location: location.instance,
      ...mockCollapsablePanelProps,
    };

    render(<MockApp {...mockProps} />);
    await waitForComponentToLoad();

    fireEvent.click(await screen.findByText('Camunda Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    fireEvent.click(await screen.findByText('Running Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%7D&gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    fireEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    fireEvent.click(await screen.findByText('Filters'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%7D&gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    fireEvent.click(await screen.findByText('Incidents'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?filter=%7B%22incidents%22%3Atrue%7D&gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });
});
