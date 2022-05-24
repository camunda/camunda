/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SortableHeader, Header, Label, SortIcon} from './styled';
import {useLocation, useNavigate} from 'react-router-dom';
import {getSortParams} from 'modules/utils/filter';

const INITIAL_SORT_ORDER = 'desc';

function toggleSorting(
  search: string,
  sortKey: string,
  currentSortOrder?: 'asc' | 'desc'
) {
  const params = new URLSearchParams(search);

  if (currentSortOrder === undefined) {
    params.set('sort', `${sortKey}+${INITIAL_SORT_ORDER}`);
    return params.toString();
  }

  params.set(
    'sort',
    `${sortKey}+${currentSortOrder === 'asc' ? 'desc' : 'asc'}`
  );

  return params.toString();
}

type Props = {
  disabled?: boolean;
  label: string | React.ReactNode;
  sortKey?: string;
  isDefault?: boolean;
  showExtraPadding?: boolean;
  paddingWidth?: number;
};

const ColumnHeader: React.FC<Props> = ({
  sortKey,
  disabled,
  label,
  isDefault = false,
  showExtraPadding = false,
  paddingWidth = 0,
}) => {
  const isSortable = sortKey !== undefined;
  const navigate = useNavigate();
  const location = useLocation();
  const existingSortParams = getSortParams();

  if (isSortable) {
    const isActive =
      existingSortParams !== null
        ? existingSortParams.sortBy === sortKey
        : isDefault;

    const displaySortIcon = isActive && !disabled;
    const currentSortOrder =
      existingSortParams?.sortOrder === undefined && isDefault
        ? INITIAL_SORT_ORDER
        : existingSortParams?.sortBy === sortKey
        ? existingSortParams?.sortOrder
        : undefined;

    return (
      <SortableHeader
        disabled={disabled}
        onClick={() => {
          navigate({
            search: toggleSorting(location.search, sortKey, currentSortOrder),
          });
        }}
        title={`Sort by ${label}`}
        data-testid={`sort-by-${sortKey}`}
        $showExtraPadding={!displaySortIcon}
        $paddingWidth={22}
      >
        <Label active={isActive} disabled={disabled}>
          {label}
        </Label>
        {displaySortIcon && <SortIcon sortOrder={currentSortOrder ?? 'desc'} />}
      </SortableHeader>
    );
  }

  return (
    <Header $showExtraPadding={showExtraPadding} $paddingWidth={paddingWidth}>
      <Label disabled={disabled}>{label}</Label>
    </Header>
  );
};

export {ColumnHeader};
