/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {DetailsModal} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';

const baseAuditLog: AuditLog = {
  auditLogKey: '123',
  entityKey: '1',
  operationType: 'UPDATE',
  entityType: 'VARIABLE',
  result: 'SUCCESS',
  actorId: 'demo',
  timestamp: '2024-01-01T10:00:00.000Z',
  annotation: 'Updated variable',
  actorType: 'USER',
  category: 'USER_TASKS',
};

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <MemoryRouter initialEntries={['/']}>
    <Routes>
      <Route path="/" element={children} />
    </Routes>
  </MemoryRouter>
);

describe('DetailsModal', () => {
  it('renders details for a normal audit log', () => {
    render(<DetailsModal isOpen onClose={() => {}} auditLog={baseAuditLog} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByRole('heading', {name: /update variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Actor')).toBeInTheDocument();
    expect(screen.getByText('Date')).toBeInTheDocument();
    expect(screen.getByText('Success')).toBeInTheDocument();
    expect(screen.getByText('demo')).toBeInTheDocument();
    expect(screen.getByText('2024-01-01 10:00:00')).toBeInTheDocument();

    expect(
      screen.queryByText(/this operation is part of a batch\./i),
    ).not.toBeInTheDocument();
  });

  it('renders batch information and link for batch audit log', () => {
    const batchAuditLog: AuditLog = {
      ...baseAuditLog,
      batchOperationKey: '999',
    };

    render(
      <DetailsModal isOpen onClose={() => {}} auditLog={batchAuditLog} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getByText(/this operation is part of a batch\./i),
    ).toBeInTheDocument();

    const link = screen.getByRole('link', {
      name: 'View batch operation 999',
    });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/batch-operations/999');
  });
});
