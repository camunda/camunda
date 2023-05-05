/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {authenticationStore} from 'modules/stores/authentication';
import {Decision} from '..';
import {createWrapper} from './mocks';

jest.mock('modules/feature-flags', () => ({
  IS_DECISION_DEFINITION_DELETION_ENABLED: true,
}));

describe('<Decision /> - operations', () => {
  beforeEach(() => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
    mockFetchDecisionXML().withSuccess(mockDmnXml);
  });

  it('should show delete button when version is selected', async () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByRole('button', {
        name: /^delete decision definition "invoiceClassification - version 1"$/i,
      })
    ).toBeInTheDocument();
  });

  it('should not show delete button when no decision is selected', () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when no version is selected', () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no permissions', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no resource based permissions', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<Decision />, {
      wrapper: createWrapper(
        '/decisions?name=invoice-assign-approver&version=1'
      ),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      })
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
  });
});
