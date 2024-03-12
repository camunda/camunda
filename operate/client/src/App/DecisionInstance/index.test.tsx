/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {useEffect} from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {DecisionInstance} from './';
import {drdStore} from 'modules/stores/drd';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchDrdData} from 'modules/mocks/api/decisionInstances/fetchDrdData';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
import {Paths} from 'modules/Routes';

const DECISION_INSTANCE_ID = '4294980768';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    drdStore.init();

    return () => {
      drdStore.reset();
    };
  }, []);

  return (
    <MemoryRouter
      initialEntries={[Paths.decisionInstance(DECISION_INSTANCE_ID)]}
    >
      <Routes>
        <Route path={Paths.decisionInstance()} element={children} />
      </Routes>
    </MemoryRouter>
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
    expect(await screen.findByTestId('diagram-body')).toBeInTheDocument();

    expect(
      await screen.findByText('Definitions Name Mock'),
    ).toBeInTheDocument();

    expect(document.title).toBe(
      `Operate: Decision Instance ${DECISION_INSTANCE_ID} of ${invoiceClassification.decisionName}`,
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
    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionXML().withSuccess(mockDmnXml);

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

    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionXML().withSuccess(mockDmnXml);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

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
});
