/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {Header} from 'App/Layout/Header';
import {mockServer} from 'modules/mock-server/node';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {LocationLog} from 'modules/utils/LocationLog';
import {rest} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';

function reset() {
  jest.clearAllTimers();
  jest.useRealTimers();
  groupedDecisionsStore.reset();
  localStorage.clear();
}

function getWrapper(initialPath: string = '/decisions') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Header />
          {children}
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

const MOCK_FILTERS_PARAMS = {
  name: 'invoice-assign-approver',
  version: '2',
  evaluated: 'true',
  failed: 'true',
  decisionInstanceIds: '2251799813689540-1',
  processInstanceId: '2251799813689549',
  evaluationDate: '1111-11-11',
} as const;

describe('<Filters />', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedDecisions))
      )
    );
    await groupedDecisionsStore.fetchDecisions();
    jest.useFakeTimers();
  });

  afterEach(reset);

  it('should render the correct elements', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(/^decision$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/version/i)).toBeInTheDocument();
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
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();
  });

  it('should write filters to url', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.selectOptions(screen.getByLabelText(/name/i), [
      'invoice-assign-approver',
    ]);
    await user.selectOptions(screen.getByLabelText(/version/i), ['2']);
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
    await user.click(screen.getByText(/evaluation date/i));
    await user.type(screen.getByText(/evaluation date/i), '1111-11-11');

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

  it('initialise filter values from url', () => {
    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    expect(
      screen.getByDisplayValue(/assign approver group/i)
    ).toBeInTheDocument();
    expect(
      within(screen.getByLabelText(/version/i)).getByRole('option', {name: '2'})
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluated/i)).toBeChecked();
    expect(screen.getByLabelText(/failed/i)).toBeChecked();
    expect(screen.getByDisplayValue(/2251799813689540-1/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/2251799813689549/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/1111-11-11/i)).toBeInTheDocument();
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
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/decision instance key\(s\)/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/process instance key/i));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/evaluation date/i));

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
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();
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
    await user.click(screen.getByText(/evaluation date/i));

    expect(
      screen.getByLabelText(/decision instance key\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance key/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();

    await user.click(screen.getByTestId('delete-decisionInstanceIds'));
    await user.click(screen.getByTestId('delete-processInstanceId'));
    await user.click(screen.getByTestId('delete-evaluationDate'));

    expect(
      screen.queryByLabelText(/decision instance key\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();
  });

  it('should select decision name and version', async () => {
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
  });

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

    expect(
      screen.queryByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/decision instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/decision instance key\(s\)/i));

    expect(
      screen.queryByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).not.toBeInTheDocument();

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

    expect(
      screen.queryByText(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      )
    ).not.toBeInTheDocument();
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

    expect(
      screen.queryByText('Key has to be a 16 to 19 digit number')
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/process instance key/i), '1');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/process instance key/i));

    expect(
      screen.queryByText('Key has to be a 16 to 19 digit number')
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/process instance key/i),
      '1111111111111111, 2222222222222222'
    );

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/process instance key/i));

    expect(
      screen.queryByText('Key has to be a 16 to 19 digit number')
    ).not.toBeInTheDocument();
  });

  it('should validate evaluation date', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(/evaluation date/i));
    await user.type(screen.getByLabelText(/evaluation date/i), 'a');

    expect(
      await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/evaluation date/i));

    expect(
      screen.queryByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/evaluation date/i), '2021-05');

    expect(
      await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();
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

    await user.click(screen.getByText('Dashboard'));
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText('Decisions'));
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });

  it('should omit all versions option', async () => {
    reset();
    const [firstDecision] = groupedDecisions;
    const [, firstVersion] = firstDecision.decisions;
    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json([{...firstDecision, decisions: [firstVersion]}]))
      )
    );
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
  });
});
