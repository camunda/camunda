/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate} from 'react-router-dom';
import {TableHeader} from '@carbon/react';
import {getSortParams, toggleSorting, INITIAL_SORT_ORDER} from './sortUtils';
import {z} from 'zod';

const sortDirectionSchema = z
  .enum(['asc', 'desc'])
  .transform((value) => value.toUpperCase())
  .pipe(z.enum(['ASC', 'DESC', 'NONE']))
  .default('NONE');

function getCurrentSortOrder(params: {
  sortParams: ReturnType<typeof getSortParams>;
  isDefault: boolean;
  sortKey: string;
}): 'asc' | 'desc' | undefined {
  const {sortParams, isDefault, sortKey} = params;

  if (sortParams?.sortOrder === undefined && isDefault) {
    return INITIAL_SORT_ORDER;
  }

  if (sortParams?.sortBy === sortKey) {
    return sortParams?.sortOrder;
  }

  return undefined;
}

type Props = {
  label: string;
  sortKey: string | undefined;
  isDefault: boolean;
  isDisabled: boolean;
  children: React.ReactNode;
};

const ColumnHeader: React.FC<Props> = ({
  sortKey,
  label,
  isDefault,
  isDisabled,
  children,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const sortParams = getSortParams(location.search);

  if (!sortKey || isDisabled) {
    return <TableHeader>{children}</TableHeader>;
  }

  const isActive =
    sortParams !== null ? sortParams.sortBy === sortKey : isDefault;

  const currentSortOrder = getCurrentSortOrder({
    sortParams,
    isDefault,
    sortKey,
  });

  return (
    <TableHeader
      onClick={() => {
        navigate({
          search: toggleSorting(location.search, sortKey, currentSortOrder),
        });
      }}
      isSortHeader
      title={`Sort by ${label}`}
      aria-label={`Sort by ${label}`}
      sortDirection={
        isActive ? sortDirectionSchema.safeParse(currentSortOrder).data : 'NONE'
      }
      isSortable
    >
      {children}
    </TableHeader>
  );
};

export {ColumnHeader};
