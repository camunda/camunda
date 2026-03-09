/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, type ButtonSize} from '@carbon/react';
import {
  Error,
  Tools,
  RetryFailed,
  MigrateAlt,
  PlayOutline,
  type CarbonIconType,
} from '@carbon/react/icons';

type ItemProps = {
  type:
    | 'RESOLVE_INCIDENT'
    | 'MIGRATE_PROCESS_INSTANCE'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'RESUME_PROCESS_INSTANCE'
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
      icon?: CarbonIconType;
      testId: string;
      isDangerous?: boolean;
      label: string;
      kind?: React.ComponentProps<typeof Button>['kind'];
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
  RESUME_PROCESS_INSTANCE: {
    testId: 'resume-operation',
    label: 'Resume',
    icon: PlayOutline,
    kind: 'primary',
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
  const {testId, label, isDangerous, icon, kind} = TYPE_DETAILS[type];
  const buttonKind = kind ?? 'ghost';

  if (useIcons && icon) {
    return (
      <li>
        <Button
          kind={buttonKind}
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
        kind={buttonKind}
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
