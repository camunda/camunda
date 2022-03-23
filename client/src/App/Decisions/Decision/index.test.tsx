/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {render, screen} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {Decision} from '.';
import {MemoryRouter} from 'react-router-dom';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}
describe('<Decision />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/decisions/:decisionDefinitionId/xml', (req, res, ctx) => {
        if (req.params.decisionDefinitionId === '2') {
          return res.once(ctx.status(404), ctx.text(''));
        }
        return res.once(ctx.text(mockDmnXml));
      }),
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedDecisions))
      )
    );

    decisionXmlStore.init();
    groupedDecisionsStore.fetchDecisions();
  });

  afterEach(() => {
    decisionXmlStore.reset();
    groupedDecisionsStore.reset();
  });

  it('should render decision table', async () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByText('DecisionTable view mock')
    ).toBeInTheDocument();
  });

  it('should render text when no decision is selected', () => {
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
  });

  it('should render text when no version is selected', () => {
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
  });

  it('should render text on error', async () => {
    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=calc-key-figures&version=1'),
    });

    expect(
      await screen.findByText(/data could not be fetched/i)
    ).toBeInTheDocument();
  });
});
