/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter} from 'react-router-dom';
import {act, render, screen} from 'modules/testing-library';
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
      <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
    );
  };

  return Wrapper;
}

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
