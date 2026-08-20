/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {ItemKeyCell} from './ItemKeyCell';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <MemoryRouter>{children}</MemoryRouter>
);

describe('<ItemKeyCell />', () => {
  it('should render the fallback text', () => {
    render(<ItemKeyCell itemKey="-1" fallbackText="No process instance" />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('No process instance')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('should render a link when the process instance key is available', () => {
    render(
      <ItemKeyCell
        itemKey="123"
        fallbackText="No process instance"
        to="/processes/123"
        label="View process instance 123"
      />,
      {wrapper: Wrapper},
    );

    const link = screen.getByRole('link', {name: 'View process instance 123'});
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/processes/123');
    expect(link).toHaveTextContent('123');
  });

  it('should render plain text when the incident key is available but no link is provided', () => {
    render(
      <ItemKeyCell itemKey="incident-key-1" fallbackText="No incident" />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText('incident-key-1')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
