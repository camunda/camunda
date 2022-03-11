/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {render, screen} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {Decision} from '.';

describe('<Decision />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    decisionXmlStore.init();
  });

  afterEach(() => {
    decisionXmlStore.reset();
  });

  it('should render decision table', async () => {
    render(<Decision />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText('DecisionTable view mock')
    ).toBeInTheDocument();
  });
});
