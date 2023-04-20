/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {AppHeader} from 'App/Layout/AppHeader';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {pickDateTimeRange} from 'modules/testUtils/dateTimeRange';
import {useEffect} from 'react';
import {IS_COMBOBOX_ENABLED} from 'modules/feature-flags';
import {
  clearComboBox,
  selectDecision,
  selectDecisionVersion,
} from 'modules/testUtils/selectComboBoxOption';

jest.unmock('modules/utils/date/formatDate');

function reset() {
  jest.clearAllTimers();
  jest.useRealTimers();
  localStorage.clear();
}

function getWrapper(initialPath: string = '/decisions') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return groupedDecisionsStore.reset;
    }, []);

    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <AppHeader />
          {children}
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

const expectVersion = (version: string) => {
  expect(
    within(screen.getByLabelText('Version', {selector: 'button'})).getByText(
      version
    )
  ).toBeInTheDocument();
};

const MOCK_FILTERS_PARAMS = {
  name: 'invoice-assign-approver',
  version: '2',
  evaluated: 'true',
  failed: 'true',
  decisionInstanceIds: '2251799813689540-1',
  processInstanceId: '2251799813689549',
} as const;

describe('<Filters />', () => {
  beforeEach(async () => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    await groupedDecisionsStore.fetchDecisions();
    jest.useFakeTimers();
  });

  afterEach(reset);

  it('should render the correct elements', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(/^decision$/i)).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toBeInTheDocument();
    expect(
      screen.getByLabelText('Version', {selector: 'button'})
    ).toBeInTheDocument();
    expect(screen.getByText(/^instance states$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluated/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/failed/i)).toBeInTheDocument();
    expect(screen.getByText(/^more filters$/i)).toBeInTheDocument();
    expect(
      screen.queryByLabelText(/decision instance key\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/evaluation date range/i)
    ).not.toBeInTheDocument();
  });

  it('should write filters to url', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    if (IS_COMBOBOX_ENABLED) {
      await selectDecision({user, option: 'Assign Approver Group'});
      await selectDecisionVersion({user, option: '2'});
    } else {
      await user.selectOptions(screen.getByLabelText(/name/i), [
        'invoice-assign-approver',
      ]);
      await user.selectOptions(screen.getByLabelText(/version/i), ['2']);
    }
    await user.click(screen.getByLabelText(/evaluated/i));
    await user.click(screen.getByLabelText(/failed/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/decision instance key\(s\)/i));
    await user.type(
      screen.getByText(/decision instance key\(s\)/i),
      '2251799813689540-1'
    );

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/process instance key/i));
    await user.type(
      screen.getByText(/process instance key/i),
      '2251799813689549'
    );

    await user.click(screen.getByText(/^more filters$/i));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(
            screen.getByTestId('search').textContent ?? ''
          ).entries()
        )
      ).toEqual(MOCK_FILTERS_PARAMS)
    );

    await user.click(screen.getByRole('button', {name: /reset/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });

  it('should write filters to url - evaluation date range', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Evaluation Date Range'));
    await user.click(screen.getByLabelText('Evaluation Date Range'));
    const evaluationDate = await pickDateTimeRange({
      user,
      screen,
      fromDay: '15',
      toDay: '20',
      fromTime: '20:30:00',
      toTime: '11:03:59',
    });
    await user.click(screen.getByText('Apply'));

    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(
            screen.getByTestId('search').textContent ?? ''
          ).entries()
        )
      ).toEqual({
        evaluationDateAfter: evaluationDate.fromDate,
        evaluationDateBefore: evaluationDate.toDate,
      })
    );

    await user.click(screen.getByRole('button', {name: /reset/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });

  it('initialise filter values from url', () => {
    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    if (IS_COMBOBOX_ENABLED) {
      expect(screen.getByLabelText('Name')).toHaveValue(
        'Assign Approver Group'
      );
      expectVersion('2');
    } else {
      expect(
        screen.getByDisplayValue(/assign approver group/i)
      ).toBeInTheDocument();
      expect(
        within(screen.getByLabelText(/version/i)).getByRole('option', {
          name: '2',
        })
      ).toBeInTheDocument();
    }

    expect(screen.getByLabelText(/evaluated/i)).toBeChecked();
    expect(screen.getByLabelText(/failed/i)).toBeChecked();
    expect(screen.getByDisplayValue(/2251799813689540-1/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/2251799813689549/i)).toBeInTheDocument();
  });

  it('initialise filter values from url - evaluation date range', async () => {
    const MOCK_PARAMS = {
      evaluationDateAfter: '2021-02-21 09:00:00',
      evaluationDateBefore: '2021-02-22 10:00:00',
    } as const;

    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_PARAMS)}`),
    });

    expect(
      await screen.findByDisplayValue(
        '2021-02-21 09:00:00 - 2021-02-22 10:00:00'
      )
    ).toBeInTheDocument();
  });

  it('should remove enabled filters on unmount', async () => {
    const {unmount, user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByLabelText(/decision instance key\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/evaluation date range/i)
    ).not.toBeInTheDocument();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/decision instance key\(s\)/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/process instance key/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/evaluation date range/i));

    expect(
      screen.getByLabelText(/decision instance key\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance key/i)).toBeInTheDocument();

    unmount();

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByLabelText(/decision instance key\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/evaluation date range/i)
    ).not.toBeInTheDocument();
  });

  it('should hide optional filters', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/decision instance key\(s\)/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/process instance key/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/evaluation date range/i));

    expect(
      screen.getByLabelText(/decision instance key\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance key/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date range/i)).toBeInTheDocument();

    await user.click(screen.getByTestId('delete-decisionInstanceIds'));
    await user.click(screen.getByTestId('delete-processInstanceId'));
    await user.click(screen.getByTestId('delete-evaluationDateRange'));

    expect(
      screen.queryByLabelText(/decision instance key\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/evaluation date range/i)
    ).not.toBeInTheDocument();
  });

  (IS_COMBOBOX_ENABLED ? it.skip : it)(
    'should select decision name and version',
    async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      const withinVersionField = within(screen.getByLabelText(/version/i));

      expect(
        withinVersionField.queryByRole('option', {name: '1'})
      ).not.toBeInTheDocument();
      expect(
        withinVersionField.queryByRole('option', {name: '2'})
      ).not.toBeInTheDocument();
      expect(screen.getAllByDisplayValue(/all/i)).toHaveLength(2);
      expect(screen.getByLabelText(/version/i)).toBeDisabled();

      await user.selectOptions(screen.getByLabelText(/name/i), [
        'invoice-assign-approver',
      ]);

      expect(
        withinVersionField.getByRole('option', {name: '2'})
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/version/i)).not.toBeDisabled();
      expect(screen.queryByDisplayValue(/all/i)).not.toBeInTheDocument();

      await user.selectOptions(screen.getByLabelText(/version/i), ['1']);

      expect(
        withinVersionField.getByRole('option', {name: '1'})
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/version/i)).not.toBeDisabled();

      await user.selectOptions(screen.getByLabelText(/name/i), ['']);

      expect(
        withinVersionField.queryByRole('option', {name: '1'})
      ).not.toBeInTheDocument();
      expect(
        withinVersionField.queryByRole('option', {name: '2'})
      ).not.toBeInTheDocument();
      expect(screen.getAllByDisplayValue(/all/i)).toHaveLength(2);
      expect(screen.getByLabelText(/version/i)).toBeDisabled();
    }
  );

  (IS_COMBOBOX_ENABLED ? it : it.skip)(
    'should select decision name and version',
    async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      expect(
        screen.getByLabelText('Version', {selector: 'button'})
      ).toBeDisabled();

      await selectDecision({user, option: 'Assign Approver Group'});
      await waitFor(() => expectVersion('2'));

      expect(
        screen.getByLabelText('Version', {selector: 'button'})
      ).toBeEnabled();

      await selectDecisionVersion({user, option: '1'});
      await waitFor(() => expectVersion('1'));
      expect(
        screen.getByLabelText('Version', {selector: 'button'})
      ).toBeEnabled();

      await clearComboBox({user, fieldName: 'Name'});
      expect(
        screen.getByLabelText('Version', {selector: 'button'})
      ).toBeDisabled();
    }
  );

  it('should validate decision instance keys', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/decision instance key\(s\)/i));
    await user.type(screen.getByLabelText(/decision instance key\(s\)/i), 'a');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/decision instance key\(s\)/i));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    );

    await user.type(screen.getByLabelText(/decision instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/decision instance key\(s\)/i));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    );

    await user.type(
      screen.getByLabelText(/decision instance key/i),
      '2251799813689549'
    );

    expect(
      await screen.findByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/decision instance key\(s\)/i));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    );
  });

  it('should validate process instance key', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/process instance key/i));
    await user.type(screen.getByLabelText(/process instance key/i), 'a');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/process instance key/i));

    await waitForElementToBeRemoved(() =>
      screen.queryByText('Key has to be a 16 to 19 digit number')
    );

    await user.type(screen.getByLabelText(/process instance key/i), '1');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/process instance key/i));

    await waitForElementToBeRemoved(() =>
      screen.queryByText('Key has to be a 16 to 19 digit number')
    );

    await user.type(
      screen.getByLabelText(/process instance key/i),
      '1111111111111111, 2222222222222222'
    );

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/process instance key/i));

    await waitForElementToBeRemoved(() =>
      screen.queryByText('Key has to be a 16 to 19 digit number')
    );
  });

  it('should reset applied filters on navigation', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/process instance key/i));
    await user.type(
      screen.getByLabelText(/process instance key/i),
      '2251799813729387'
    );

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?processInstanceId=2251799813729387$/
      )
    );

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).getByRole('link', {
        name: /dashboard/i,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).getByRole('link', {
        name: /decisions/i,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });

  (IS_COMBOBOX_ENABLED ? it.skip : it)(
    'should omit all versions option',
    async () => {
      reset();
      const firstDecision = groupedDecisions[0]!;
      const firstVersion = firstDecision.decisions[1]!;

      mockFetchGroupedDecisions().withSuccess([
        {...firstDecision, decisions: [firstVersion]},
      ]);

      await groupedDecisionsStore.fetchDecisions();

      jest.useFakeTimers();

      const {user} = render(<Filters />, {
        wrapper: getWrapper(
          `/decisions?name=${firstDecision.decisionId}&version=${firstVersion}`
        ),
      });

      await user.click(screen.getByLabelText(/version/i));

      expect(
        within(screen.queryByLabelText(/version/i)!).queryByRole('option', {
          name: /all/i,
        })
      ).not.toBeInTheDocument();
    }
  );
});
