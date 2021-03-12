/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';
import {getSorting} from 'modules/utils/filter';
import {useHistory} from 'react-router-dom';
import {IS_FILTERS_V2} from 'modules/feature-flags';

function toggleSorting(search: string, column: string) {
  const params = new URLSearchParams(search);
  const {sortBy, sortOrder} = getSorting();

  if (params.get('sort') === null) {
    params.set('sort', `${column}+desc`);
  }

  if (sortBy === column) {
    if (sortOrder === 'asc') {
      params.set('sort', `${column}+desc`);
    } else {
      params.set('sort', `${column}+asc`);
    }
  } else {
    params.set('sort', `${column}+desc`);
  }

  return params.toString();
}

type Props = {
  disabled?: boolean;
  label: string;
  sortKey?: string;
  onSort?: (sortKey: string) => void;
  sorting?: {
    sortBy: string;
    sortOrder: 'asc' | 'desc';
  };
};

function getSortOrder({
  disabled,
  sortKey,
  sorting,
  sortBy,
  sortOrder,
}: Pick<Props, 'disabled' | 'sortKey' | 'sorting'> & {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}) {
  if (disabled) {
    return undefined;
  }

  if (IS_FILTERS_V2) {
    return sortKey === sortBy ? sortOrder : undefined;
  }

  return sorting?.sortBy === sortKey && sorting?.sortBy !== undefined
    ? sorting?.sortOrder
    : undefined;
}

const ColumnHeader: React.FC<Props> = ({
  sortKey,
  onSort,
  disabled,
  sorting,
  label,
}) => {
  const isSortable = sortKey !== undefined;
  const history = useHistory();
  const {sortBy, sortOrder} = getSorting();
  const isActive =
    (isSortable && IS_FILTERS_V2 && sortKey === sortBy) ||
    (isSortable && sorting?.sortBy === sortKey);

  if (isSortable) {
    return (
      <Styled.SortColumnHeader
        disabled={disabled}
        onClick={() => {
          if (!disabled && sortKey !== undefined) {
            if (IS_FILTERS_V2) {
              history.push({
                ...history.location,
                search: toggleSorting(history.location.search, sortKey),
              });
            } else {
              onSort?.(sortKey);
            }
          }
        }}
        title={`Sort by ${sortKey}`}
        data-testid={`sort-by-${sortKey}`}
      >
        <Styled.Label active={isActive} disabled={disabled}>
          {label}
        </Styled.Label>
        <Styled.SortIcon
          active={isActive}
          disabled={disabled}
          sortOrder={getSortOrder({
            sorting,
            sortKey,
            disabled,
            sortBy,
            sortOrder,
          })}
        />
      </Styled.SortColumnHeader>
    );
  }

  return (
    <Styled.ColumnHeader>
      <Styled.Label active={isActive} disabled={disabled}>
        {label}
      </Styled.Label>
    </Styled.ColumnHeader>
  );
};

export default ColumnHeader;
