/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {SortableHeader, Header, Label, SortIcon} from './styled';
import {useHistory} from 'react-router-dom';
import {getSortParams} from 'modules/utils/filter';

function toggleSorting(
  search: string,
  column: string,
  sortParams: {
    sortBy: string;
    sortOrder: 'asc' | 'desc';
  } | null
) {
  const params = new URLSearchParams(search);

  if (sortParams === null) {
    params.set('sort', `${column}+desc`);
    return params.toString();
  }

  const {sortBy, sortOrder} = sortParams;

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
  isDefault?: boolean;
};

const ColumnHeader: React.FC<Props> = ({
  sortKey,
  disabled,
  label,
  isDefault,
}) => {
  const isSortable = sortKey !== undefined;

  const history = useHistory();

  const sortParams = getSortParams();

  if (isSortable) {
    const isActive =
      sortParams !== null ? sortParams.sortBy === sortKey : isDefault;

    const displaySortIcon = isActive && !disabled;
    return (
      <SortableHeader
        disabled={disabled}
        onClick={() => {
          history.push({
            ...history.location,
            search: toggleSorting(history.location.search, sortKey, sortParams),
          });
        }}
        title={`Sort by ${label}`}
        data-testid={`sort-by-${sortKey}`}
        $showExtraPadding={!displaySortIcon}
      >
        <Label active={isActive} disabled={disabled}>
          {label}
        </Label>
        {displaySortIcon && (
          <SortIcon sortOrder={sortParams?.sortOrder ?? 'desc'} />
        )}
      </SortableHeader>
    );
  }

  return (
    <Header>
      <Label disabled={disabled}>{label}</Label>
    </Header>
  );
};

export {ColumnHeader};
