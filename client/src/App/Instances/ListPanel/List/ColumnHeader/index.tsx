/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

type Props = {
  disabled?: boolean;
  label: string;
  sortKey?: string;
  onSort?: (...args: any[]) => any;
  sorting?: any;
};

function ColumnHeader(props: Props) {
  const isSortable = Boolean(props.sortKey);
  const {sortKey, onSort, disabled, sorting} = props;
  const Component = isSortable ? Styled.SortColumnHeader : Styled.ColumnHeader;
  const componentProps = isSortable
    ? {
        disabled,
        onClick: () => {
          // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
          !disabled && onSort(sortKey);
        },
        title: `Sort by ${sortKey}`,
        'data-testid': `sort-by-${sortKey}`,
      }
    : {disabled};

  const isActive = isSortable && props.sorting.sortBy === props.sortKey;

  return (
    <Component {...componentProps}>
      <Styled.Label active={isActive} disabled={props.disabled}>
        {props.label}
      </Styled.Label>
      {isSortable && (
        <Styled.SortIcon
          active={isActive}
          disabled={props.disabled}
          sortOrder={
            !disabled && sorting.sortBy === sortKey ? sorting.sortOrder : null
          }
        />
      )}
    </Component>
  );
}

export default ColumnHeader;
