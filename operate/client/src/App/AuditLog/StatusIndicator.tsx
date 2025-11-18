/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CheckmarkFilled, ErrorFilled} from '@carbon/react/icons';

type StatusIndicatorProps = {
  status: string;
};

const StatusIndicator: React.FC<StatusIndicatorProps> = ({status}) => {
  const isSuccess = status === 'success';
  const Icon = isSuccess ? CheckmarkFilled : ErrorFilled;
  const color = isSuccess
    ? 'var(--cds-support-success)'
    : 'var(--cds-support-error)';
  const text = isSuccess ? 'Success' : 'Failed';

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--cds-spacing-03)',
      }}
    >
      <Icon size={16} style={{color}} />
      <span>{text}</span>
    </div>
  );
};

export {StatusIndicator};

