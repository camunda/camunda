/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import {Stack} from '@carbon/react';

import './EmptyState.scss';

interface EmptyStateProps {
  icon?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}

export default function EmptyState({icon, title, description, actions}: EmptyStateProps) {
  return (
    <div className="EmptyState">
      {icon}
      <Stack gap={6} className="content">
        <Stack gap={2}>
          <div className="title">{title}</div>
          {description && <div className="description">{description}</div>}
        </Stack>
        {actions && (
          <Stack gap={4} orientation="horizontal" className="actions">
            {actions}
          </Stack>
        )}
      </Stack>
    </div>
  );
}
