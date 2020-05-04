/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import Header from './Header';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createInstance} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import PropTypes from 'prop-types';
import {
  render,
  within,
  fireEvent,
  findByText,
  getByTestId,
} from '@testing-library/react';
import {countStore, location, countStoreWithCount} from './Header.setup';

// props mocks
const mockCollapsablePanelProps = {
  getStateLocally: () => ({}),
  isFiltersCollapsed: false,
  expandFilters: jest.fn(),
};

jest.mock('modules/api/instances', () => ({
  fetchWorkflowCoreStatistics: jest.fn().mockImplementation(() => ({
    coreStatistics: {
      running: 821,
      active: 90,
      withIncidents: 731,
    },
  })),
}));
jest.mock('modules/api/header', () => ({
  fetchUser: jest.fn().mockImplementation(() => ({
    firstname: 'firstname',
    lastname: 'lastname',
  })),
}));

// Header component fetches user information in the background, which is an async action and might complete after the tests finished.
// Tests also depend on the statistics fetch to be completed, in order to test the results that are rendered in the screen.
// So we have to use this function in all tests, in order to verify all the async actions are completed, when we want them to be completed.
const waitForComponentToLoad = async (container) => {
  expect(await findByText(container, 'firstname lastname')).toBeInTheDocument();
  expect(
    within(getByTestId(container, 'header-link-incidents')).getByTestId('badge')
  ).toHaveTextContent(731);
};

describe('Header', () => {
  let dataManager;

  const MockApp = (props) => (
    <Router history={props.history ? props.history : createMemoryHistory()}>
      <ThemeProvider>
        <Header.WrappedComponent {...props} />
      </ThemeProvider>
    </Router>
  );

  MockApp.propTypes = {
    history: PropTypes.object,
  };

  beforeEach(() => {
    dataManager = createMockDataManager();
  });

  it('should render all header links', async () => {
    const mockProps = {
      location: location.dashboard,
      countStore,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {getByText, queryByText, container} = render(
      <MockApp {...mockProps} />
    );

    await waitForComponentToLoad(container);

    expect(getByText('Camunda Operate')).toBeInTheDocument();
    expect(getByText('Dashboard')).toBeInTheDocument();
    expect(getByText('Running Instances')).toBeInTheDocument();
    expect(getByText('Filters')).toBeInTheDocument();
    expect(getByText('Incidents')).toBeInTheDocument();
    expect(queryByText('Instance')).toBeNull();
  });

  it('should render incident, filter and instances counts correctly', async () => {
    const mockProps = {
      location: location.dashboard,
      countStore,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByTestId} = render(<MockApp {...mockProps} />);

    expect(
      within(getByTestId('header-link-incidents')).getByTestId('badge')
    ).toBeEmpty();
    expect(
      within(getByTestId('header-link-filters')).getByTestId('badge')
    ).toBeEmpty();
    expect(
      within(getByTestId('header-link-instances')).getByTestId('badge')
    ).toBeEmpty();

    await waitForComponentToLoad(container);

    expect(
      within(getByTestId('header-link-incidents')).getByTestId('badge')
    ).toHaveTextContent(731);
    expect(
      within(getByTestId('header-link-filters')).getByTestId('badge')
    ).toHaveTextContent(821);
    expect(
      within(getByTestId('header-link-instances')).getByTestId('badge')
    ).toHaveTextContent(821);
  });

  it('should render user element', async () => {
    const mockProps = {
      location: location.dashboard,
      countStore,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByText} = render(<MockApp {...mockProps} />);

    await waitForComponentToLoad(container);
    expect(getByText('firstname lastname')).toBeInTheDocument();
  });

  it('should highlight links correctly on dashboard page', async () => {
    const mockProps = {
      location: location.dashboard,
      countStore,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByText} = render(<MockApp {...mockProps} />);
    await waitForComponentToLoad(container);

    expect(getByText('Dashboard')).not.toHaveStyleRule('opacity', '0.5');
    expect(getByText('Running Instances')).toHaveStyleRule('opacity', '0.5');
    expect(getByText('Filters')).toHaveStyleRule('opacity', '0.5');
    expect(getByText('Incidents')).toHaveStyleRule('opacity', '0.5');
  });

  it('should highlight links correctly on instances page', async () => {
    const mockProps = {
      location: location.instances,
      countStore,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByText} = render(<MockApp {...mockProps} />);
    await waitForComponentToLoad(container);

    expect(getByText('Dashboard')).toHaveStyleRule('opacity', '0.5');
    expect(getByText('Running Instances')).not.toHaveStyleRule(
      'opacity',
      '0.5'
    );
    expect(getByText('Filters')).not.toHaveStyleRule('opacity', '0.5');
    expect(getByText('Incidents')).toHaveStyleRule('opacity', '0.5');
  });

  it('should render instance details skeleton on instance view', async () => {
    const mockProps = {
      location: location.instance,
      countStore: countStoreWithCount,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByTestId, queryByTestId} = render(
      <MockApp {...mockProps} />
    );
    await waitForComponentToLoad(container);

    expect(queryByTestId('instance-detail')).not.toBeInTheDocument();
    expect(getByTestId('instance-skeleton-circle')).toBeInTheDocument();
    expect(getByTestId('instance-skeleton-block')).toBeInTheDocument();
  });

  it('should render instance details on instance view', async () => {
    const MOCK_INSTANCE_ID = 'instance_id';

    const mockProps = {
      location: location.instance,
      countStore: countStoreWithCount,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByText, getByTestId, queryByTestId} = render(
      <MockApp {...mockProps} />
    );
    await waitForComponentToLoad(container);

    const subscriptions = dataManager.subscriptions();

    subscriptions['LOAD_INSTANCE']({
      state: 'LOADED',
      response: {...createInstance({id: MOCK_INSTANCE_ID})},
    });

    expect(getByText(`Instance ${MOCK_INSTANCE_ID}`)).toBeInTheDocument();
    expect(getByTestId('instance-detail')).toBeInTheDocument();
    expect(queryByTestId('instance-skeleton-circle')).not.toBeInTheDocument();
    expect(queryByTestId('instance-skeleton-block')).not.toBeInTheDocument();
  });

  it('should render instance details on refresh', async () => {
    const MOCK_FIRST_INSTANCE_ID = 'first_instance_id';
    const MOCK_SECOND_INSTANCE_ID = 'second_instance_id';
    const mockProps = {
      location: location.instance,
      countStore: countStoreWithCount,
      dataManager,
      ...mockCollapsablePanelProps,
    };
    const {container, getByText, queryByText} = render(
      <MockApp {...mockProps} />
    );
    await waitForComponentToLoad(container);

    const subscriptions = dataManager.subscriptions();

    subscriptions['LOAD_INSTANCE']({
      state: 'LOADED',
      response: {...createInstance({id: MOCK_FIRST_INSTANCE_ID})},
    });

    expect(getByText(`Instance ${MOCK_FIRST_INSTANCE_ID}`)).toBeInTheDocument();

    subscriptions['CONSTANT_REFRESH']({
      state: 'LOADED',
      response: {
        LOAD_INSTANCE: {...createInstance({id: MOCK_SECOND_INSTANCE_ID})},
      },
    });
    expect(queryByText(`Instance ${MOCK_FIRST_INSTANCE_ID}`)).toBeNull();

    expect(
      getByText(`Instance ${MOCK_SECOND_INSTANCE_ID}`)
    ).toBeInTheDocument();
  });

  it('should go to the correct pages when clicking on header links', async () => {
    const MOCK_HISTORY = createMemoryHistory();

    const mockProps = {
      history: MOCK_HISTORY,
      location: location.instance,
      countStore: countStoreWithCount,
      dataManager,
      ...mockCollapsablePanelProps,
    };

    const {container, findByText} = render(<MockApp {...mockProps} />);
    await waitForComponentToLoad(container);

    fireEvent.click(await findByText('Camunda Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');

    fireEvent.click(await findByText('Running Instances'));
    let searchParams = new URLSearchParams(MOCK_HISTORY.location.search);
    expect(searchParams.get('filter')).toBe('{"active":true,"incidents":true}');

    fireEvent.click(await findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');

    fireEvent.click(await findByText('Filters'));
    searchParams = new URLSearchParams(MOCK_HISTORY.location.search);
    expect(searchParams.get('filter')).toBe('{"active":true,"incidents":true}');

    fireEvent.click(await findByText('Incidents'));
    searchParams = new URLSearchParams(MOCK_HISTORY.location.search);
    expect(searchParams.get('filter')).toBe('{"incidents":true}');
  });
});
