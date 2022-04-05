/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {SORT_ORDER} from 'modules/constants';
import * as Styled from './styled';

type Props = {
  sortOrder?: 'asc' | 'desc';
  onClick?: (...args: any[]) => any;
  disabled?: boolean;
};

function SortIcon(props: Props) {
  return (
    <Styled.SortIcon {...props} data-testid={`${props.sortOrder}-icon`}>
      {props.sortOrder === SORT_ORDER.ASC ? (
        <Styled.Up data-testid="sort-icon" $sortOrder={props.sortOrder} />
      ) : (
        <Styled.Down data-testid="sort-icon" $sortOrder={props.sortOrder} />
      )}
    </Styled.SortIcon>
  );
}
SortIcon.defaultProps = {
  disabled: false,
};

export default SortIcon;
