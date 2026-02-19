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
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {Header} from '../index';
import {MemoryRouter} from 'react-router-dom';
import {createUser} from 'modules/testUtils';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import * as clientConfig from 'modules/utils/getClientConfig';

const MOCK_DECISION_INSTANCE_ID = '123567';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('InstanceHeader', () => {
  it('should render multi tenancy column and include tenant in version link', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    render(
      <Header
        decisionEvaluationInstanceKey={MOCK_DECISION_INSTANCE_ID}
        onChangeDrdPanelState={() => void 0}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(await screen.findByText('Default Tenant')).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'View decision "Invoice Classification version 1" instances - Default Tenant',
      }),
    ).toHaveAttribute(
      'href',
      `${Paths.decisions()}?${new URLSearchParams({
        version: '1',
        name: 'invoiceClassification',
        evaluated: 'true',
        failed: 'true',
        tenant: '<default>',
      })}`,
    );
  });

  it('should hide multi tenancy column and exclude tenant from version link', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    render(
      <Header
        decisionEvaluationInstanceKey={MOCK_DECISION_INSTANCE_ID}
        onChangeDrdPanelState={() => void 0}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Default Tenant')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: 'View decision "Invoice Classification version 1" instances',
      }),
    ).toBeInTheDocument();
  });
});
