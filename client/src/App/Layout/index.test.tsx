/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter} from 'react-router-dom';
import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {modificationsStore} from 'modules/stores/modifications';
import {Layout} from '.';

type Props = {
  children?: React.ReactNode;
};

function getWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('Layout', () => {
  afterEach(() => {
    modificationsStore.reset();
  });

  it('should not display footer when modification mode is enabled', async () => {
    render(<Layout />, {wrapper: getWrapper('/processes/1')});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();

    modificationsStore.enableModificationMode();
    expect(screen.queryByText(/All rights reserved/)).not.toBeInTheDocument();
  });

  it('should not display footer in processes page', async () => {
    render(<Layout />, {wrapper: getWrapper('/processes')});

    expect(screen.queryByText(/All rights reserved/)).not.toBeInTheDocument();
  });

  it('should not display footer in decisions page', async () => {
    render(<Layout />, {wrapper: getWrapper('/decisions')});

    expect(screen.queryByText(/All rights reserved/)).not.toBeInTheDocument();
  });

  it('should display footer in process instance page', async () => {
    render(<Layout />, {wrapper: getWrapper('/processes/1')});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();
  });

  it('should display footer in decision instance page', async () => {
    render(<Layout />, {wrapper: getWrapper('/decisions/1')});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();
  });

  it('should display footer in dashboard page', async () => {
    render(<Layout />, {wrapper: getWrapper()});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();
  });
});
