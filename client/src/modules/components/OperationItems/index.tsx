/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  children: React.ReactNode;
};

export default function OperationItems(props: Props) {
  return (
    <Styled.Ul {...props}>{React.Children.toArray(props.children)}</Styled.Ul>
  );
}

type ItemProps = {
  type: OperationEntityType;
  onClick?: (...args: unknown[]) => any;
  title?: string;
};

const Item: React.FC<ItemProps> = function ({title, onClick, type, ...rest}) {
  return (
    <Styled.Li onClick={onClick}>
      <Styled.Button {...rest} type="button" title={title}>
        {type === 'RESOLVE_INCIDENT' && (
          <Styled.RetryIcon data-testid="retry-operation-icon" />
        )}
        {type === 'CANCEL_PROCESS_INSTANCE' && (
          <Styled.CancelIcon data-testid="cancel-operation-icon" />
        )}
        {type === 'DELETE_PROCESS_INSTANCE' && (
          <Styled.DeleteIcon data-testid="delete-operation-icon" />
        )}
      </Styled.Button>
    </Styled.Li>
  );
};

OperationItems.Item = Item;
