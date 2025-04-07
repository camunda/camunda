/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';

import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {removeOptionalFilter} from 'modules/testUtils/removeOptionalFilter';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.unmock('modules/utils/date/formatDate');

describe('Optional Filters', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should initially hide optional filters', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(
      screen.queryByTestId('optional-filter-variable-name'),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key\(s\)/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/operation id/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/Parent Process Instance Key/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/error message/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/start date range/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/end date range/i)).not.toBeInTheDocument();
  });

  it('should display variable fields on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    await user.click(screen.getByTestId('optional-filter-menuitem-variable'));
    expect(
      screen.getByTestId('optional-filter-variable-name'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    expect(
      screen.queryByTestId('optional-filter-menuitem-variable'),
    ).not.toBeInTheDocument();
  });

  it('should display instance ids field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(screen.getByTestId('optional-filter-menuitem-ids'));
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    expect(
      screen.getByLabelText(/^process instance key\(s\)$/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('optional-filter-menuitem-ids'),
    ).not.toBeInTheDocument();
  });

  it('should display operation id field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-operationId'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    expect(screen.getByLabelText(/^operation id$/i)).toBeInTheDocument();
    expect(
      screen.queryByTestId('optional-filter-menuitem-operationId'),
    ).not.toBeInTheDocument();
  });

  it('should display Parent Process Instance Key field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-parentInstanceId'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    expect(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('optional-filter-menuitem-parentInstanceId'),
    ).not.toBeInTheDocument();
  });

  it('should display error message field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-errorMessage'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    expect(screen.getByLabelText(/^error message$/i)).toBeInTheDocument();
    expect(
      screen.queryByTestId('optional-filter-menuitem-errorMessage'),
    ).not.toBeInTheDocument();
  });

  it('should display start date field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-startDateRange'),
    );

    expect(screen.getByLabelText(/^start date range$/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    expect(
      screen.queryByTestId('optional-filter-menuitem-startDateRange'),
    ).not.toBeInTheDocument();
  });

  it('should display end date field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-endDateRange'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));

    expect(screen.getByLabelText(/^end date range$/i)).toBeInTheDocument();
    expect(
      screen.queryByTestId('optional-filter-menuitem-endDateRange'),
    ).not.toBeInTheDocument();
  });

  it('should hide more filters button when all optional filters are visible', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(screen.getByTestId('optional-filter-menuitem-variable'));
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(screen.getByTestId('optional-filter-menuitem-ids'));
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-operationId'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-parentInstanceId'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-errorMessage'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-startDateRange'),
    );
    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(
      screen.getByTestId('optional-filter-menuitem-endDateRange'),
    );

    expect(
      screen.queryByTestId('more-filters-dropdown'),
    ).not.toBeInTheDocument();

    await user.hover(screen.getByTestId('optional-filter-variable-name'));
    await user.click(screen.getByLabelText(`Remove Variable Filter`));

    expect(
      screen.getByRole('button', {name: /^more filters$/i}),
    ).toBeInTheDocument();
  });

  it('should delete optional filters', async () => {
    const MOCK_PARAMS = {
      process: 'bigVarProcess',
      version: '1',
      ids: '2251799813685467',
      parentInstanceId: '1954699813693756',
      errorMessage: 'a random error',
      startDateBefore: '2021-02-21 18:17:18',
      startDateAfter: '2021-02-21 20:00:00',
      endDateBefore: '2021-02-23 18:17:18',
      endDateAfter: '2021-02-23 22:00:00',
      flowNodeId: 'ServiceTask_0kt6c5i',
      operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
      active: 'true',
      incidents: 'true',
      completed: 'true',
      canceled: 'true',
    } as const;

    const {user} = render(<Filters />, {
      wrapper: getWrapper(
        `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
      ),
    });

    expect(screen.getByTestId('search').textContent).toBe(
      `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
    );

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(screen.getByTestId('optional-filter-menuitem-variable'));
    await user.type(screen.getByRole('textbox', {name: /^name$/i}), 'foo');
    await user.type(screen.getByRole('textbox', {name: /^value$/i}), '"bar"');

    expect(
      screen.getByLabelText(/^process instance key\(s\)$/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/^error message$/i)).toBeInTheDocument();

    expect(screen.getByLabelText(/^start date range$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^end date range$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^operation id$/i)).toBeInTheDocument();

    await removeOptionalFilter({
      user,
      screen,
      label: 'Process Instance Key(s)',
    });

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(
          Object.entries({
            process: 'bigVarProcess',
            version: '1',
            parentInstanceId: '1954699813693756',
            errorMessage: 'a random error',
            startDateBefore: '2021-02-21 18:17:18',
            startDateAfter: '2021-02-21 20:00:00',
            endDateBefore: '2021-02-23 18:17:18',
            endDateAfter: '2021-02-23 22:00:00',
            flowNodeId: 'ServiceTask_0kt6c5i',
            operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
            active: 'true',
            incidents: 'true',
            completed: 'true',
            canceled: 'true',
          }),
        ).toString()}`,
      ),
    );
    expect(
      screen.queryByLabelText('Process Instance Key(s)'),
    ).not.toBeInTheDocument();

    await removeOptionalFilter({
      user,
      screen,
      label: 'Parent Process Instance Key',
    });

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(
          Object.entries({
            process: 'bigVarProcess',
            version: '1',
            errorMessage: 'a random error',
            startDateBefore: '2021-02-21 18:17:18',
            startDateAfter: '2021-02-21 20:00:00',
            endDateBefore: '2021-02-23 18:17:18',
            endDateAfter: '2021-02-23 22:00:00',
            flowNodeId: 'ServiceTask_0kt6c5i',
            operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
            active: 'true',
            incidents: 'true',
            completed: 'true',
            canceled: 'true',
          }),
        ).toString()}`,
      ),
    );
    expect(
      screen.queryByLabelText('Parent Process Instance Key'),
    ).not.toBeInTheDocument();

    await removeOptionalFilter({user, screen, label: 'Error Message'});

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(
          Object.entries({
            process: 'bigVarProcess',
            version: '1',
            startDateBefore: '2021-02-21 18:17:18',
            startDateAfter: '2021-02-21 20:00:00',
            endDateBefore: '2021-02-23 18:17:18',
            endDateAfter: '2021-02-23 22:00:00',
            flowNodeId: 'ServiceTask_0kt6c5i',
            operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
            active: 'true',
            incidents: 'true',
            completed: 'true',
            canceled: 'true',
          }),
        ).toString()}`,
      ),
    );
    expect(screen.queryByLabelText('Error Message')).not.toBeInTheDocument();

    await removeOptionalFilter({user, screen, label: 'Start Date Range'});

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(
          Object.entries({
            process: 'bigVarProcess',
            version: '1',
            endDateBefore: '2021-02-23 18:17:18',
            endDateAfter: '2021-02-23 22:00:00',
            flowNodeId: 'ServiceTask_0kt6c5i',
            operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
            active: 'true',
            incidents: 'true',
            completed: 'true',
            canceled: 'true',
          }),
        ).toString()}`,
      ),
    );
    expect(screen.queryByLabelText('Start Date Range')).not.toBeInTheDocument();

    await removeOptionalFilter({user, screen, label: 'End Date Range'});

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(
          Object.entries({
            process: 'bigVarProcess',
            version: '1',
            flowNodeId: 'ServiceTask_0kt6c5i',
            operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
            active: 'true',
            incidents: 'true',
            completed: 'true',
            canceled: 'true',
          }),
        ).toString()}`,
      ),
    );
    expect(screen.queryByLabelText('End Date Range')).not.toBeInTheDocument();

    expect(screen.getByRole('textbox', {name: /^name$/i})).toBeInTheDocument();
    expect(screen.getByRole('textbox', {name: /^value$/i})).toBeInTheDocument();
    await user.hover(screen.getByTestId('optional-filter-variable-name'));
    await user.click(screen.getByLabelText(`Remove Variable Filter`));

    expect(screen.getByTestId('search').textContent).toBe(
      `?${new URLSearchParams(
        Object.entries({
          process: 'bigVarProcess',
          version: '1',
          flowNodeId: 'ServiceTask_0kt6c5i',
          operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
        }),
      ).toString()}`,
    );
    expect(
      screen.queryByTestId('optional-filter-variable-name'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /^name$/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /^value$/i}),
    ).not.toBeInTheDocument();

    await removeOptionalFilter({user, screen, label: 'Operation Id'});

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(
          Object.entries({
            process: 'bigVarProcess',
            version: '1',
            flowNodeId: 'ServiceTask_0kt6c5i',
            active: 'true',
            incidents: 'true',
            completed: 'true',
            canceled: 'true',
          }),
        ).toString()}`,
      ),
    );
    expect(screen.queryByLabelText('Operation Id')).not.toBeInTheDocument();
  });

  it('should remove optional filters on filter reset', async () => {
    const MOCK_PARAMS = {
      process: 'bigVarProcess',
      version: '1',
      ids: '2251799813685467',
      parentInstanceId: '1954699813693756',
      errorMessage: 'a random error',
      startDateBefore: '2021-02-21 18:17:18',
      startDateAfter: '2021-02-21 20:00:00',
      endDateBefore: '2021-02-23 18:17:18',
      endDateAfter: '2021-02-23 22:00:00',
      flowNodeId: 'ServiceTask_0kt6c5i',
      operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
      active: 'true',
      incidents: 'true',
      completed: 'true',
      canceled: 'true',
    } as const;

    const {user} = render(<Filters />, {
      wrapper: getWrapper(
        `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
      ),
    });

    await user.click(screen.getByRole('button', {name: /^more filters$/i}));
    await user.click(screen.getByTestId('optional-filter-menuitem-variable'));
    await user.type(screen.getByRole('textbox', {name: /^name$/i}), 'foo');
    await user.type(screen.getByRole('textbox', {name: /^value$/i}), '"bar"');

    expect(screen.getByTestId('search').textContent).toBe(
      `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
    );

    expect(
      screen.getByLabelText(/^process instance key\(s\)$/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/^error message$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^start date range$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^end date range$/i)).toBeInTheDocument();
    expect(
      screen.getByTestId('optional-filter-variable-name'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/^value$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^operation id$/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /reset filters/i}));

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /\?active=true&incidents=true$/,
      ),
    );

    expect(
      screen.queryByLabelText(/^process instance key\(s\)$/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/^Parent Process Instance Key$/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^error message$/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/^start date range$/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/^end date range$/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('optional-filter-variable-name'),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^value$/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^operation id$/i)).not.toBeInTheDocument();
  });
});
