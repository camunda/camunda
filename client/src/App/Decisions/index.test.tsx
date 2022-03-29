/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render} from '@testing-library/react';
import {rest} from 'msw';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Decisions} from './';

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/decisions']}>
        <Routes>
          <Route path="/decisions" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('<Decisions />', () => {
  it('should show page title', () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({}))
      ),
      rest.get('/api/decisions/grouped', (_, res, ctx) => res(ctx.json({})))
    );

    render(<Decisions />, {wrapper: Wrapper});

    expect(document.title).toBe('Operate: Decision Instances');
  });
});
