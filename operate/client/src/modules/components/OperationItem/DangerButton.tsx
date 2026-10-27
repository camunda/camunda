/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, type ButtonSize} from '@carbon/react';
import {TrashCan, type CarbonIconType} from '@carbon/react/icons';

type ItemProps = {
  type: 'DELETE';
  onClick: () => void;
  title: string;
  disabled?: boolean;
  isIconOnly?: boolean;
  size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<
  Record<
    ItemProps['type'],
    {icon: CarbonIconType; label: string; testId: string}
  >
> = {
  DELETE: {icon: TrashCan, label: 'Delete', testId: 'delete-operation'},
};

const DangerButton: React.FC<ItemProps> = ({
  title,
  onClick,
  type,
  disabled,
  isIconOnly = false,
  size,
}) => {
  const {icon, label, testId} = TYPE_DETAILS[type];
  const buttonProps = {
    kind: 'danger--ghost',
    iconDescription: title,
    onClick,
    disabled,
    'data-testid': testId,
    'aria-label': title,
    size,
  } satisfies React.ComponentProps<typeof Button>;

  return (
    <li>
      {isIconOnly ? (
        <Button
          {...buttonProps}
          renderIcon={icon}
          tooltipPosition="left"
          hasIconOnly
        />
      ) : (
        <Button {...buttonProps} title={title}>
          {label}
        </Button>
      )}
    </li>
  );
};

export {DangerButton};
