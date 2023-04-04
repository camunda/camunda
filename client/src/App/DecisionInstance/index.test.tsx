/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DecisionInstance} from './';
import {drdStore} from 'modules/stores/drd';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchDrdData} from 'modules/mocks/api/decisionInstances/fetchDrdData';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

const DECISION_INSTANCE_ID = '4294980768';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    drdStore.init();

    return () => {
      drdStore.reset();
    };
  }, []);

  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/decisions/${DECISION_INSTANCE_ID}`]}>
        <Routes>
          <Route path="/decisions/:decisionInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('<DecisionInstance />', () => {
  beforeEach(() => {
    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionXML().withSuccess(mockDmnXml);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
  });

  it('should set page title', async () => {
    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    expect(
      await screen.findByText('Definitions Name Mock')
    ).toBeInTheDocument();

    expect(document.title).toBe(
      `Operate: Decision Instance ${DECISION_INSTANCE_ID} of ${invoiceClassification.decisionName}`
    );
  });

  it('should close DRD panel', async () => {
    const {user} = render(<DecisionInstance />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: 'Close DRD Panel'})
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should maximize DRD panel and hide other panels', async () => {
    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      })
    );

    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-header')
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('decision-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-variables-panel')
    ).not.toBeInTheDocument();
  });

  it('should minimize DRD panel', async () => {
    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      })
    );
    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Minimize DRD Panel',
      })
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should show DRD panel on header button click', async () => {
    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    await user.click(
      await screen.findByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    await waitFor(() =>
      expect(
        within(screen.getByTestId('decision-instance-header')).getByRole(
          'button',
          {name: /open decision requirements diagram/i}
        )
      ).toBeEnabled()
    );

    await user.click(
      within(screen.getByTestId('decision-instance-header')).getByRole(
        'button',
        {name: /open decision requirements diagram/i}
      )
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should persist panel state', async () => {
    const {unmount, user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    await user.click(
      await screen.findByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();

    unmount();

    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
  });

  it('should not keep same tab selected when page is completely refreshed', async () => {
    const {user, unmount, rerender} = render(<DecisionInstance />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      })
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: /result/i,
      })
    );

    expect(
      screen.queryByRole('heading', {
        name: /inputs/i,
      })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {
        name: /outputs/i,
      })
    ).not.toBeInTheDocument();

    rerender(<DecisionInstance />);

    expect(
      screen.queryByRole('heading', {
        name: /inputs/i,
      })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {
        name: /outputs/i,
      })
    ).not.toBeInTheDocument();

    unmount();

    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionXML().withSuccess(mockDmnXml);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      })
    ).toBeInTheDocument();
  });

  it('should display forbidden content', async () => {
    mockFetchDecisionInstance().withServerError(403);
    render(<DecisionInstance />, {wrapper: Wrapper});

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information'
      )
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about permissions'})
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions'
    );
  });
});
