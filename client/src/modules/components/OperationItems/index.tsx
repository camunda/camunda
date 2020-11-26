/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {OperationType} from 'modules/types';
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
  type: OperationType;
  onClick?: (...args: unknown[]) => any;
  title?: string;
};

const Item: React.FC<ItemProps> = function ({title, onClick, type, ...rest}) {
  return (
    <Styled.Li onClick={onClick}>
      {/* @ts-expect-error */}
      <Styled.Button {...rest} type={type} title={title}>
        {type === 'RESOLVE_INCIDENT' && <Styled.RetryIcon />}
        {type === 'CANCEL_WORKFLOW_INSTANCE' && <Styled.CancelIcon />}
      </Styled.Button>
    </Styled.Li>
  );
};

OperationItems.Item = Item;
