/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.RESOLVE_INCIDENT]: <Styled.RetryIcon />,
  [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: <Styled.CancelIcon />,
} as const;

type Props = {
  children: React.ReactNode;
};

export default function OperationItems(props: Props) {
  return (
    <Styled.Ul {...props}>{React.Children.toArray(props.children)}</Styled.Ul>
  );
}

type ItemProps = {
  type: 'RESOLVE_INCIDENT' | 'CANCEL_WORKFLOW_INSTANCE';
  onClick?: (...args: unknown[]) => any;
  title?: string;
};

const Item: React.FC<ItemProps> = function ({title, onClick, type, ...rest}) {
  if (iconsMap.hasOwnProperty(type)) {
    return (
      <Styled.Li onClick={onClick}>
        {/* @ts-expect-error */}
        <Styled.Button {...rest} type={type} title={title}>
          {iconsMap[type]}
        </Styled.Button>
      </Styled.Li>
    );
  }

  return null;
};

OperationItems.Item = Item;
