/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CSSProperties} from 'react';
import {Column, UseSortByColumnProps, UseSortByOptions} from 'react-table';

import {t} from 'translation';

import {Head} from './Table';

type Entry = Head;
export function flatten(ctx: string = '', suffix: (entry: Entry) => string | undefined = () => '') {
  return (flat: string[], entry: Entry): string[] => {
    if (typeof entry === 'object' && entry.columns) {
      // nested column, flatten recursivly with augmented context
      return flat.concat(
        entry.columns.reduce<string[]>(flatten(ctx + (entry.id || entry.label), suffix), [])
      );
    } else {
      // normal column, return current context with optional suffix
      return flat.concat(ctx + suffix(entry));
    }
  };
}

// We have to do this, because when header is sortable, style props are only passed to the button that is rendered inside the header
// see implementation here: https://github.com/carbon-design-system/carbon/blob/main/packages/react/src/components/DataTable/TableHeader.tsx#L179
export function rewriteHeaderStyles(styles?: CSSProperties) {
  return function (th: HTMLTableCellElement | null) {
    if (!th) {
      return;
    }

    if (styles) {
      Object.entries(styles).forEach(([key, value]) => {
        // @ts-expect-error this simply rewrites the style
        th.style[key] = value;
      });
    }
  };
}

export function formatSorting<T extends object>(
  sorting: {by: string; order: string} | undefined,
  resultType: string | undefined,
  columns: (Column & Partial<UseSortByOptions<T> & UseSortByColumnProps<T>>)[],
  allowLocalSorting: boolean
): {id?: string; desc?: boolean; order?: string}[] {
  if (allowLocalSorting) {
    const firstSortableColumn = columns.find((column) => !column.disableSortBy);
    if (firstSortableColumn) {
      return [{id: firstSortableColumn.id, desc: false}];
    }
    return [];
  }

  if (!sorting) {
    return [];
  }
  const {by, order} = sorting;
  let id = by;
  if (resultType === 'map') {
    if (columns[0]?.id && (by === 'label' || by === 'key')) {
      id = columns[0]?.id;
    } else if (columns[1]?.id && by === 'value') {
      id = columns[1]?.id;
    }
  }
  return [{id, desc: order === 'desc'}];
}

export function convertHeaderNameToAccessor(name: string) {
  const joined = name
    .split(' ')
    .join('')
    .replace(t('report.variables.default').toString(), t('report.groupBy.variable') + ':');

  return joined.charAt(0).toLowerCase() + joined.slice(1);
}
