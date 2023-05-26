/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import {Header} from '..';
import {Wrapper} from './mocks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';

describe('<Header />', () => {
  it('should render a header', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByRole('banner', {name: 'Camunda Tasklist'}),
    ).toBeInTheDocument();
    expect(screen.getByText('Non-Production License')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Open Info'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Open Settings'}),
    ).toBeInTheDocument();
  });
});
