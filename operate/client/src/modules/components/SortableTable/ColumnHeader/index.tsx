/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useLocation, useNavigate} from 'react-router-dom';
import {getSortParams} from 'modules/utils/filter';
import {Header, TableHeader} from '@carbon/react';

const INITIAL_SORT_ORDER = 'desc';

function toggleSorting(
  search: string,
  sortKey: string,
  currentSortOrder?: 'asc' | 'desc',
) {
  const params = new URLSearchParams(search);
  if (currentSortOrder === undefined) {
    params.set('sort', `${sortKey}+${INITIAL_SORT_ORDER}`);
    return params.toString();
  }

  params.set(
    'sort',
    `${sortKey}+${currentSortOrder === 'asc' ? 'desc' : 'asc'}`,
  );
  return params.toString();
}

type Props = {
  label: string | React.ReactNode;
  sortKey: string;
  isDefault?: boolean;
  isDisabled?: boolean;
  onSort?: (sortKey: string) => void;
} & React.ComponentProps<typeof Header>;

const ColumnHeader: React.FC<Props> = ({
  sortKey,
  label,
  isDefault = false,
  isDisabled = false,
  onSort,
  ...rest
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const existingSortParams = getSortParams(location.search);

  const isActive =
    existingSortParams !== null
      ? existingSortParams.sortBy === sortKey
      : isDefault;

  const displaySortIcon = isActive && !isDisabled;
  const currentSortOrder =
    existingSortParams?.sortOrder === undefined && isDefault
      ? INITIAL_SORT_ORDER
      : existingSortParams?.sortBy === sortKey
        ? existingSortParams?.sortOrder
        : undefined;

  return (
    <TableHeader
      {...rest}
      onClick={() => {
        onSort?.(sortKey);
        navigate({
          search: toggleSorting(location.search, sortKey, currentSortOrder),
        });
      }}
      isSortHeader={!isDisabled}
      title={`Sort by ${label}`}
      aria-label={`Sort by ${label}`}
      sortDirection={
        displaySortIcon
          ? currentSortOrder === 'asc'
            ? 'ASC'
            : currentSortOrder === 'desc'
              ? 'DESC'
              : 'NONE'
          : 'NONE'
      }
      isSortable={!isDisabled}
    >
      {label}
    </TableHeader>
  );
};

export {ColumnHeader};
