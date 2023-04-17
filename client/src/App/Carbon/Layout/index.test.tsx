/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter} from 'react-router-dom';
import {act, render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {modificationsStore} from 'modules/stores/modifications';
import {Layout} from '.';
import {useEffect} from 'react';

type Props = {
  children?: React.ReactNode;
};

function getWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<Props> = ({children}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
      };
    }, []);

    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

const OperationsPanelMock: React.FC = () => <div>OperationsPanelMock</div>;

jest.mock('modules/components/OperationsPanel', () => ({
  OperationsPanel: OperationsPanelMock,
}));

describe.skip('Layout', () => {
  it('should not display footer when modification mode is enabled', async () => {
    render(<Layout />, {wrapper: getWrapper('/processes/1')});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
    });

    expect(screen.queryByText(/All rights reserved/)).not.toBeInTheDocument();
  });

  it('should render processes page', async () => {
    render(<Layout />, {wrapper: getWrapper('/processes')});

    expect(screen.queryByText(/All rights reserved/)).not.toBeInTheDocument();
    expect(screen.getByText('OperationsPanelMock')).toBeInTheDocument();
  });

  it('should render decisions page', async () => {
    render(<Layout />, {wrapper: getWrapper('/decisions')});

    expect(screen.queryByText(/All rights reserved/)).not.toBeInTheDocument();
    expect(screen.getByText('OperationsPanelMock')).toBeInTheDocument();
  });

  it('should render process instance page', async () => {
    render(<Layout />, {wrapper: getWrapper('/processes/1')});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();
    expect(screen.queryByText('OperationsPanelMock')).not.toBeInTheDocument();
  });

  it('should render decision instance page', async () => {
    render(<Layout />, {wrapper: getWrapper('/decisions/1')});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();
    expect(screen.queryByText('OperationsPanelMock')).not.toBeInTheDocument();
  });

  it('should render dashboard page', async () => {
    render(<Layout />, {wrapper: getWrapper()});

    expect(screen.getByText(/All rights reserved/)).toBeInTheDocument();
    expect(screen.queryByText('OperationsPanelMock')).not.toBeInTheDocument();
  });
});
