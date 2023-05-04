/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Error, Tools, RetryFailed} from '@carbon/react/icons';
import {Button} from '@carbon/react';
import {Icon} from 'carbon-components-react';

type ItemProps = {
  type:
    | 'RESOLVE_INCIDENT'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'ENTER_MODIFICATION_MODE';
  onClick: () => void;
  title: string;
  disabled?: boolean;
};

const TYPE_DETAILS: Readonly<
  Record<
    ItemProps['type'],
    {icon?: typeof Icon; testId: string; isDangerous?: boolean; label?: string}
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
        hasIconOnly
      />
    </li>
  );
};

export {OperationItem};
