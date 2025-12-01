/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockSearchDecisionDefinitions} from 'modules/mocks/api/v2/decisionDefinitions/searchDecisionDefinitions';
import {mockDecisionDefinitions} from 'modules/mocks/mockDecisionDefinitions';
import {Decision} from '..';
import {createWrapper} from './mocks';

vi.mock('modules/feature-flags', () => ({
  IS_DECISION_DEFINITION_DELETION_ENABLED: true,
}));

describe('<Decision /> - operations', () => {
  beforeEach(() => {
    const selectedDecisionDefinition = mockDecisionDefinitions.items[5];
    mockSearchDecisionDefinitions().withSuccess({
      items: [selectedDecisionDefinition],
      page: {totalItems: 1},
    });
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
  });

  it('should show delete button when version is selected', async () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByRole('button', {
        name: 'Delete Decision Definition "invoiceClassification - Version 1"',
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
});
