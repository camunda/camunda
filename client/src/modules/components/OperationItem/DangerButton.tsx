/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, ButtonSize} from '@carbon/react';

type ItemProps = {
  type: 'DELETE';
  onClick: () => void;
  title: string;
  disabled?: boolean;
  size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<
  Record<ItemProps['type'], {testId: string; label?: string}>
> = {
  DELETE: {testId: 'delete-operation', label: 'Delete'},
};

const DangerButton: React.FC<ItemProps> = ({
  title,
  onClick,
  type,
  disabled,
  size,
}) => {
  const {testId, label} = TYPE_DETAILS[type];
  return (
    <li>
      <Button
        kind="danger--ghost"
        tooltipPosition="left"
        iconDescription={title}
        onClick={onClick}
        disabled={disabled}
        data-testid={testId}
        title={title}
        aria-label={title}
        size={size}
      >
        {label}
      </Button>
    </li>
  );
};

export {DangerButton};
