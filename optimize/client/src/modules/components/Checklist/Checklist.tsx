/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChangeEvent, useState} from 'react';
import classnames from 'classnames';
import {
  TableSelectAll,
  TableSelectRow,
  TableToolbar,
  TableToolbarContent,
  TableToolbarSearch,
} from '@carbon/react';
import {useId} from '@camunda/camunda-optimize-composite-components';

import {Table, TableBody, TableHead, NoDataNotice} from 'components';
import {t} from 'translation';

import './Checklist.scss';

interface ChecklistProps<T> {
  onSearch?: (value: string) => void;
  allItems: T[];
  selectedItems: T[];
  onChange: (values: (T | undefined)[]) => void;
  formatter: (
    allItems: T[],
    selectedItems: T[]
  ) => {
    label: string | JSX.Element[] | null;
    id: string | number | boolean | null | undefined;
    checked?: boolean;
    disabled?: boolean;
  }[];
  loading?: boolean;
  labels?: Record<string, string | JSX.Element[]>;
  headerHidden?: boolean;
  preItems?: TableBody | false;
  customHeader?: string | JSX.Element[];
  title?: string | JSX.Element[];
  columnLabel?: string | JSX.Element[];
  hideSelectAllInView?: boolean;
}

export default function Checklist<
  T extends string | boolean | number | null | undefined | {id: string | null; key?: string},
>({
  onSearch = () => {},
  selectedItems,
  allItems,
  onChange,
  formatter,
  loading,
  labels = {
    search: t('common.multiSelect.search'),
    empty: t('common.multiSelect.empty'),
  },
  headerHidden,
  preItems,
  customHeader,
  title,
  columnLabel,
  hideSelectAllInView,
}: ChecklistProps<T>) {
  const [query, setQuery] = useState('');
  const checklistId = useId();

  const formattedData = formatter(allItems, selectedItems);
  const isAllSelected = formattedData.every(({checked}) => checked);
  const isSomeSelected = !isAllSelected && formattedData.some(({checked}) => checked);

  const searchFilteredData = formattedData.filter(({label, id}) =>
    (label || id)?.toString().toLowerCase().includes(query.toLowerCase())
  );

  const isAllSelectedInSearch = searchFilteredData.every(({checked}) => checked);

  const updateItems = (itemId: string | number | boolean | null | undefined, checked: boolean) => {
    if (checked) {
      const itemToSelect = allItems.find((item) => getIdentifier(item) === itemId);
      onChange([...selectedItems, itemToSelect]);
    } else {
      onChange(selectedItems.filter((item) => getIdentifier(item) !== itemId));
    }
  };

  const selectAllItems = () => onChange(allItems);

  const selectAllItemsInSearch = () => {
    const selectableIds = searchFilteredData.filter(({checked}) => !checked).map(({id}) => id);
    const itemsToSelect = allItems.filter((item) => selectableIds.includes(getIdentifier(item)));
    return onChange([...selectedItems, ...itemsToSelect]);
  };

  const deselectAllItems = () => onChange([]);

  const deselectAllItemsInSearch = () => {
    const selectedIds = searchFilteredData.filter(({checked}) => checked).map(({id}) => id);
    const itemsToRemove = selectedItems.filter(
      (item) => !selectedIds.includes(getIdentifier(item))
    );
    return onChange(itemsToRemove);
  };

  const head: TableHead[] = [
    {
      label:
        formattedData.length > 1 && !customHeader && !headerHidden ? (
          <TableSelectAll
            id="checked"
            key="checked"
            name="checked"
            ariaLabel="checked"
            indeterminate={isSomeSelected}
            checked={isAllSelected}
            onSelect={({target}) => {
              const {checked} = target as HTMLInputElement;
              if (checked) {
                selectAllItems();
              } else {
                deselectAllItems();
              }
            }}
          />
        ) : (
          'checked'
        ),
      id: 'checked',
      sortable: false,
      width: 30,
    },
    {label: customHeader || columnLabel || '', id: 'name', sortable: false},
  ];

  const body: TableBody[] = searchFilteredData.map(({id, label, checked, disabled}) => {
    const rowId = `${checklistId}-${id}`;
    const onSelect = () => updateItems(id, !checked);
    const rowLabel = (label || id || '').toString();
    return {
      content: [
        <TableSelectRow
          checked={!!checked}
          id={rowId}
          name={rowId}
          ariaLabel={rowLabel}
          disabled={disabled}
          onSelect={onSelect}
        />,
        rowLabel,
      ],
      props: {
        onClick: onSelect,
      },
    };
  });

  if (preItems) {
    body.unshift(preItems);
  }

  if (!hideSelectAllInView && !loading && query && searchFilteredData.length > 1) {
    const onSelect = () =>
      !isAllSelectedInSearch ? selectAllItemsInSearch() : deselectAllItemsInSearch();
    body.unshift({
      content: [
        <TableSelectRow
          checked={isAllSelectedInSearch}
          id="selectAllInView"
          name="selectAllInView"
          ariaLabel={t('common.multiSelect.selectAll').toString()}
          onSelect={onSelect}
        />,
        t('common.multiSelect.selectAll'),
      ],
      props: {
        onClick: onSelect,
        className: 'selectAllInView',
      },
    });
  }

  return (
    <Table
      disablePagination
      useZebraStyles={false}
      className={classnames('Checklist', {
        headerHidden: formattedData.length <= 1 || headerHidden,
        customHeader,
      })}
      head={head}
      body={body}
      loading={loading}
      title={title}
      noData={<NoDataNotice title={labels.empty} />}
      toolbar={
        !headerHidden && (
          <TableToolbar>
            <TableToolbarContent>
              <TableToolbarSearch
                placeholder={labels.search?.toString()}
                onChange={(evt) => {
                  if (evt) {
                    const value = (evt as ChangeEvent<HTMLInputElement>).target.value;
                    setQuery(value);
                    onSearch(value);
                  }
                }}
                onClear={() => {
                  setQuery('');
                  onSearch('');
                }}
                expanded
                data-modal-primary-focus
              />
            </TableToolbarContent>
          </TableToolbar>
        )
      }
    />
  );
}

function getIdentifier(
  item: string | boolean | number | null | undefined | {id: string | null; key?: string}
): string | boolean | number | null | undefined {
  if (typeof item === 'object' && item !== null) {
    return item.key || item.id;
  }

  return item;
}
