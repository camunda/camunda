/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {Decision} from '.';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}
describe('<Decision />', () => {
  beforeEach(async () => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    decisionXmlStore.init();
    groupedDecisionsStore.fetchDecisions();

    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched')
    );
  });

  afterEach(() => {
    decisionXmlStore.reset();
    groupedDecisionsStore.reset();
  });

  it('should render decision table and panel header', async () => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByText('DecisionTable view mock')
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'invoiceClassification'}));
  });

  it('should render text when no decision is selected', () => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper('/decisions'),
    });

    expect(
      screen.getByText(/there is no decision selected/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /to see a decision table or a literal expression, select a decision in the filters panel/i
      )
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Decision'}));
  });

  it('should render text when no version is selected', () => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper(
        '/decisions?name=invoiceClassification&version=all'
      ),
    });

    expect(
      screen.getByText(
        /there is more than one version selected for decision "invoiceClassification"/i
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /to see a decision table or a literal expression, select a single version/i
      )
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'invoiceClassification'}));
  });

  it('should render text on error', async () => {
    mockFetchDecisionXML().withServerError(404);

    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=calc-key-figures&version=1'),
    });

    expect(
      await screen.findByText(/data could not be fetched/i)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: 'Calculate Credit History Key Figures',
      })
    );
  });
});
