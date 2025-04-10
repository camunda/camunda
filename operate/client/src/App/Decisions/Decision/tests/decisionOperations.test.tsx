/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {authenticationStore} from 'modules/stores/authentication';
import {Decision} from '..';
import {createWrapper} from './mocks';

jest.mock('modules/feature-flags', () => ({
  IS_DECISION_DEFINITION_DELETION_ENABLED: true,
}));

describe('<Decision /> - operations', () => {
  beforeEach(() => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
  });

  it('should show delete button when version is selected', async () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByRole('button', {
        name: /^delete decision definition "invoiceClassification - version 1"$/i,
      }),
    ).toBeInTheDocument();
  });

  it('should not show delete button when no decision is selected', () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when no version is selected', () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no resource based permissions', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<Decision />, {
      wrapper: createWrapper(
        '/decisions?name=invoice-assign-approver&version=1',
      ),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete decision definition/i,
      }),
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
  });
});
