/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import {Instances} from './index';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import {MemoryRouter} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';

type Props = {
  children: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ReactQueryProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ReactQueryProvider>
  );
};

describe('<Instances />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );
  });

  it('should fetch process instances', async () => {
    nodeMockServer.use(
      rest.post('/internal/users/:userId/process-instances', (_, res, ctx) => {
        return res.once(ctx.json(processInstancesMocks.processInstances));
      }),
    );

    render(<Instances />, {
      wrapper: Wrapper,
    });

    const [{process}] = processInstancesMocks.processInstances;

    expect(await screen.findAllByText(process.bpmnProcessId)).toHaveLength(4);
  });
});
