/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {PILL_TYPE} from 'modules/constants';

import * as Styled from './styled';

type Props = {
  type?: 'TIMESTAMP' | 'FILTER';
  isActive: boolean;
  isDisabled?: boolean;
  children?: React.ReactNode;
  count?: number;
  grow?: boolean;
  onClick?: (...args: any[]) => any;
  className?: string;
};

function Pill(props: Props) {
  return (
    <Styled.Pill
      aria-pressed={props.isActive}
      onClick={props.onClick}
      className={props.className}
      isActive={props.isActive}
      disabled={props.isDisabled}
      variant={props.type}
    >
      {props.type === PILL_TYPE.TIMESTAMP && (
        <Styled.Clock data-testid="target-icon" />
      )}

      <Styled.Label grow={props.grow}>{props.children}</Styled.Label>
      {props.type === PILL_TYPE.FILTER && (
        <Styled.Count>{props.count}</Styled.Count>
      )}
    </Styled.Pill>
  );
}

Pill.defaultProps = {
  isActive: false,
  grow: false,
};
export default React.memo(Pill);
