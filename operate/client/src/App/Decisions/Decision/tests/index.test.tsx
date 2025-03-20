/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {Decision} from '..';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {createWrapper} from './mocks';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

describe('<Decision />', () => {
  beforeEach(() => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
  });

  it('should render decision table and panel header', async () => {
    const originalWindowPrompt = window.prompt;
    window.prompt = jest.fn();

    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);

    const {user} = render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByText('DecisionTable view mock'),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'invoiceClassification'}));
    expect(screen.getAllByText('invoiceClassification')).toHaveLength(2);

    await user.click(
      screen.getByRole('button', {
        name: 'Decision ID / Click to copy',
      }),
    );

    expect(await screen.findByText('Copied to clipboard')).toBeInTheDocument();

    window.prompt = originalWindowPrompt;
  });

  it('should render text when no decision is selected', async () => {
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper('/decisions'),
    });

    expect(
      await screen.findByText(/there is no decision selected/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /to see a decision table or a literal expression, select a decision in the filters panel/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Decision'}));

    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );
  });

  it('should render text when no version is selected', async () => {
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper(
        '/decisions?name=invoiceClassification&version=all',
      ),
    });

    expect(
      await screen.findByText(
        /there is more than one version selected for decision "invoiceClassification"/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /to see a decision table or a literal expression, select a single version/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'invoiceClassification'}));
  });

  it('should render text on error', async () => {
    mockFetchDecisionDefinitionXML().withServerError(404);

    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=calc-key-figures&version=1'),
    });

    expect(
      await screen.findByText(/data could not be fetched/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: 'Calculate Credit History Key Figures',
      }),
    );
  });
});
