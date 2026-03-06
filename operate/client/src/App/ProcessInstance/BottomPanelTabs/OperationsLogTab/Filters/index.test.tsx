/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Filters} from './index';
import {render, screen} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';

function getWrapper(initialPath = '/processes/123') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/processes/123" element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('Process Instance OperationsLog Filters', () => {
  it('should render operation type field', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('Operation type')).toBeInTheDocument();
  });

  it('should render entity type field', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('Entity type')).toBeInTheDocument();
  });

  it('should parse filters from URL search params', async () => {
    render(<Filters />, {
      wrapper: getWrapper('/processes/123?operationType=ASSIGN'),
    });

    expect(screen.getByText(/Total items selected:\s+1/)).toBeInTheDocument();
  });

  it('should submit filters when form values change', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    const [firstComboBox] = screen.getAllByRole('combobox');
    await user.click(firstComboBox);

    const [firstCheckbox] = screen.getAllByRole('checkbox');
    await user.click(firstCheckbox);

    expect(screen.getByText(/Total items selected:\s+1/)).toBeInTheDocument();
  });
});
