/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Li,
  Button,
  RetryIcon,
  CancelIcon,
  DeleteIcon,
  ModifyIcon,
} from './styled';

type ItemProps = {
  type:
    | 'RESOLVE_INCIDENT'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'DELETE'
    | 'ENTER_MODIFICATION_MODE';
  onClick?: React.ComponentProps<'button'>['onClick'];
  title?: string;
  disabled?: boolean;
};

const OperationItem: React.FC<ItemProps> = ({
  title,
  onClick,
  type,
  disabled,
  ...rest
}) => {
  return (
    <Li disabled={disabled}>
      <Button
        {...rest}
        onClick={onClick}
        title={title}
        aria-label={title}
        disabled={disabled}
        type="button"
      >
        {type === 'RESOLVE_INCIDENT' && (
          <RetryIcon data-testid="retry-operation-icon" />
        )}
        {type === 'CANCEL_PROCESS_INSTANCE' && (
          <CancelIcon data-testid="cancel-operation-icon" />
        )}
        {type === 'DELETE' && (
          <DeleteIcon data-testid="delete-operation-icon" />
        )}
        {type === 'ENTER_MODIFICATION_MODE' && (
          <ModifyIcon data-testid="modification-mode-icon" />
        )}
      </Button>
    </Li>
  );
};

export {OperationItem};
