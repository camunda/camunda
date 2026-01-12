/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, type ButtonSize, type Icon} from '@carbon/react';
import {Error, Tools, RetryFailed, MigrateAlt} from '@carbon/react/icons';

type ItemProps = {
  type:
    | 'RESOLVE_INCIDENT'
    | 'MIGRATE_PROCESS_INSTANCE'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'ENTER_MODIFICATION_MODE';
  onClick: React.ComponentProps<'button'>['onClick'];
  title: string;
  disabled?: boolean;
  size?: ButtonSize;
  useIcons?: boolean;
};

const TYPE_DETAILS: Readonly<
  Record<
    ItemProps['type'],
    {
      testId: string;
      label: string;
      isDangerous?: boolean;
      icon?: typeof Icon;
    }
  >
> = {
  RESOLVE_INCIDENT: {
    testId: 'retry-operation',
    label: 'Retry',
    icon: RetryFailed,
  },
  MIGRATE_PROCESS_INSTANCE: {
    testId: 'migrate-operation',
    label: 'Migrate',
    icon: MigrateAlt,
  },
  CANCEL_PROCESS_INSTANCE: {
    testId: 'cancel-operation',
    label: 'Cancel',
    icon: Error,
  },
  ENTER_MODIFICATION_MODE: {
    testId: 'enter-modification-mode',
    label: 'Modify',
    icon: Tools,
  },
};

const OperationItem: React.FC<ItemProps> = ({
  title,
  onClick,
  type,
  disabled,
  size,
  useIcons = false,
}) => {
  const {testId, label, isDangerous, icon} = TYPE_DETAILS[type];

  if (useIcons && icon) {
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
  }

  return (
    <li>
      <Button
        kind="ghost"
        renderIcon={icon}
        onClick={onClick}
        disabled={disabled}
        data-testid={testId}
        aria-label={title}
        size={size}
      >
        {label}
      </Button>
    </li>
  );
};

export {OperationItem};
