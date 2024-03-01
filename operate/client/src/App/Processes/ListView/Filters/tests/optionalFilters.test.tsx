/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';

import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {removeOptionalFilter} from 'modules/testUtils/removeOptionalFilter';

jest.unmock('modules/utils/date/formatDate');

describe('Optional Filters', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

    await processXmlStore.fetchProcessXml('bigVarProcess');
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
