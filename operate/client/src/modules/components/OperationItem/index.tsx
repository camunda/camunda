/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Error, Tools, RetryFailed} from '@carbon/react/icons';
import {Button, ButtonSize} from '@carbon/react';

type ItemProps = {
  type:
    | 'RESOLVE_INCIDENT'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'ENTER_MODIFICATION_MODE';
  onClick: React.ComponentProps<'button'>['onClick'];
  title: string;
  disabled?: boolean;
  size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<
  Record<
    ItemProps['type'],
    {
      icon?: React.ElementType;
      testId: string;
      isDangerous?: boolean;
      label?: string;
    }
  >
> = {
  RESOLVE_INCIDENT: {icon: RetryFailed, testId: 'retry-operation'},
  CANCEL_PROCESS_INSTANCE: {icon: Error, testId: 'cancel-operation'},
  ENTER_MODIFICATION_MODE: {icon: Tools, testId: 'enter-modification-mode'},
};

const OperationItem: React.FC<ItemProps> = ({
  title,
  onClick,
  type,
  disabled,
  size,
}) => {
  const {icon, testId} = TYPE_DETAILS[type];

  return (
    <li>
      <Button
        kind="ghost"
        renderIcon={icon}
        tooltipPosition="left"
        iconDescription={title}
        onClick={onClick}
        disabled={disabled}
        data-testid={testId}
        title={title}
        aria-label={title}
        hasIconOnly
        size={size}
      />
    </li>
  );
};

export {OperationItem};
