/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {DecisionInstance} from './';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const DECISION_INSTANCE_ID = '4294980768';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter
        initialEntries={[Paths.decisionInstance(DECISION_INSTANCE_ID)]}
      >
        <Routes>
          <Route path={Paths.decisionInstance()} element={children} />
          <Route
            path="*"
            element={
              <>
                decisions page
                <LocationLog />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<DecisionInstance />', () => {
  beforeEach(() => {
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockSearchDecisionInstances().withSuccess({
      items: [invoiceClassification],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockMe().withSuccess(createUser());
    mockMe().withSuccess(createUser());
  });

  it('should set page title', async () => {
    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    expect(
      await screen.findByText('Definitions Name Mock'),
    ).toBeInTheDocument();

    expect(document.title).toBe(
      `Operate: Decision Instance ${DECISION_INSTANCE_ID} of ${invoiceClassification.decisionDefinitionName}`,
    );
  });

  it('should close DRD panel', async () => {
    const {user} = render(<DecisionInstance />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: 'Close DRD Panel'}),
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
    expect(screen.getByTestId('instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel'),
    ).toBeInTheDocument();
  });

  it('should maximize DRD panel and hide other panels', async () => {
    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      }),
    );

    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('instance-header')).not.toBeInTheDocument();
    expect(screen.queryByTestId('decision-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-variables-panel'),
    ).not.toBeInTheDocument();
  });

  it('should minimize DRD panel', async () => {
    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      }),
    );
    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Minimize DRD Panel',
      }),
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel'),
    ).toBeInTheDocument();
  });

  it('should show DRD panel on header button click', async () => {
    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: 'Close DRD Panel',
      }),
    );

    await user.click(
      within(screen.getByTestId('instance-header')).getByRole('button', {
        name: /open decision requirements diagram/i,
      }),
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel'),
    ).toBeInTheDocument();
  });

  it('should persist panel state', async () => {
    const {unmount, user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: 'Close DRD Panel',
      }),
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();

    unmount();

    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockSearchDecisionInstances().withSuccess({
      items: [invoiceClassification],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
  });

  it('should not keep same tab selected when page is completely refreshed', async () => {
    const {user, unmount, rerender} = render(<DecisionInstance />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('tab', {
        name: /result/i,
      }),
    );

    expect(
      screen.queryByRole('heading', {
        name: /inputs/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {
        name: /outputs/i,
      }),
    ).not.toBeInTheDocument();

    rerender(<DecisionInstance />);

    expect(
      screen.queryByRole('heading', {
        name: /inputs/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {
        name: /outputs/i,
      }),
    ).not.toBeInTheDocument();

    unmount();

    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockSearchDecisionInstances().withSuccess({
      items: [invoiceClassification],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('inputs-skeleton'));
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      }),
    ).toBeInTheDocument();
  });

  it('should display forbidden content', async () => {
    mockFetchDecisionInstance().withServerError(403);
    render(<DecisionInstance />, {wrapper: Wrapper});

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about permissions'}),
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
    );
  });

  it('should redirect to decisions page and display notification if decision instance is not found (404)', async () => {
    mockFetchDecisionInstance().withServerError(404);
    mockFetchDecisionDefinitionXML().withServerError();
    mockSearchDecisionInstances().withServerError();

    render(<DecisionInstance />, {wrapper: Wrapper});

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: `Decision instance ${DECISION_INSTANCE_ID} could not be found`,
      isDismissable: true,
    });
  });
});
