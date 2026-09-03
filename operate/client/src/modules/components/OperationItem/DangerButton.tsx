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
  size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<
  Record<ItemProps['type'], {icon: CarbonIconType; testId: string}>
> = {
  DELETE: {icon: TrashCan, testId: 'delete-operation'},
};

const DangerButton: React.FC<ItemProps> = ({
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
        kind="danger--ghost"
        renderIcon={icon}
        tooltipPosition="left"
        iconDescription={title}
        onClick={onClick}
        disabled={disabled}
        data-testid={testId}
        aria-label={title}
        hasIconOnly
        size={size}
      />
    </li>
  );
};

export {DangerButton};
