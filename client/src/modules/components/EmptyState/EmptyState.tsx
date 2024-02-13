/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import {Stack} from '@carbon/react';

import {Icon} from 'components';

import './EmptyState.scss';

interface EmptyStateProps {
  icon?: string;
  title: ReactNode;
  description: ReactNode;
  actions?: ReactNode;
}

export default function EmptyState({icon, title, description, actions}: EmptyStateProps) {
  return (
    <div className="EmptyState">
      <Icon type={icon} className="icon" />
      <Stack gap={6} className="content">
        <Stack gap={2}>
          <div className="title">{title}</div>
          <div className="description">{description}</div>
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
