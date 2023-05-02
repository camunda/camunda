/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {ReactNode, useState} from 'react';
import classnames from 'classnames';

import {LabeledInput, Tag, LoadingIndicator, SearchInput} from 'components';
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
    label: string;
    id: string | number | boolean | null;
    checked?: boolean;
    disabled?: boolean;
  }[];
  loading?: boolean;
  labels?: Record<string, string | JSX.Element[]>;
  headerHidden?: boolean;
  preItems?: ReactNode;
  customHeader?: ReactNode;
}

export default function Checklist<
  T extends string | boolean | number | null | {id: string; key?: string}
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
}: ChecklistProps<T>) {
  const [query, setQuery] = useState('');

  if (!allItems) {
    return <LoadingIndicator />;
  }

  const data = formatter(allItems, selectedItems);
  const allSelected = data.every(({checked}) => checked);
  const allDeselected = selectedItems.length === 0;

  const filteredData = data.filter(({label, id}) =>
    (label || id)?.toString().toLowerCase().includes(query.toLowerCase())
  );
  const allSelectedInView = filteredData.every(({checked}) => checked);

  const updateItems = (itemId: string | number | boolean | null, checked: boolean) => {
    if (checked) {
      const itemToSelect = allItems.find((item) => getIdentifier(item) === itemId);
      onChange([...selectedItems, itemToSelect]);
    } else {
      onChange(selectedItems.filter((item) => getIdentifier(item) !== itemId));
    }
  };

  const selectAll = () => onChange(allItems);

  const selectAllInView = () => {
    const selectableIds = filteredData.filter(({checked}) => !checked).map(({id}) => id);
    const itemsToSelect = allItems.filter((item) => selectableIds.includes(getIdentifier(item)));
    return onChange([...selectedItems, ...itemsToSelect]);
  };

  const deselectAll = () => onChange([]);

  const deselectAllInView = () => {
    const selectedIds = filteredData.filter(({checked}) => checked).map(({id}) => id);
    const itemsToRemove = selectedItems.filter(
      (item) => !selectedIds.includes(getIdentifier(item))
    );
    return onChange(itemsToRemove);
  };

  return (
    <div className="Checklist">
      {!headerHidden && (
        <div className="header">
          {data.length > 1 && !customHeader && (
            <LabeledInput
              className="selectAll"
              ref={(input) => {
                if (input != null) {
                  input.indeterminate = !allSelected && !allDeselected;
                }
              }}
              checked={allSelected}
              type="checkbox"
              label={t('common.selectAll')}
              onChange={({target: {checked}}) => (checked ? selectAll() : deselectAll())}
            />
          )}
          {customHeader && <div className="customHeader">{customHeader}</div>}
          <SearchInput
            value={query}
            className="searchInput"
            placeholder={labels.search}
            onChange={(evt) => {
              setQuery(evt.target.value);
              onSearch(evt.target.value);
            }}
            onClear={() => {
              setQuery('');
              onSearch('');
            }}
          />
          {selectedItems.length > 0 && (
            <Tag onRemove={deselectAll}>{selectedItems.length} Selected</Tag>
          )}
        </div>
      )}
      <div className="itemsList">
        {loading && <LoadingIndicator />}
        {!loading && (
          <>
            {filteredData.length === 0 && <p>{labels.empty}</p>}
            {query && filteredData.length > 1 && (
              <LabeledInput
                className={classnames('selectAllInView', {highlight: allSelectedInView})}
                type="checkbox"
                checked={allSelectedInView}
                label={t('common.multiSelect.selectAll')}
                onChange={({target: {checked}}) =>
                  checked ? selectAllInView() : deselectAllInView()
                }
              />
            )}
            {preItems}
            {filteredData.map(({id, label, checked, disabled}) => (
              <LabeledInput
                className={classnames({highlight: checked && !disabled})}
                disabled={disabled}
                key={id?.toString()}
                type="checkbox"
                checked={checked}
                label={label || id}
                onChange={({target: {checked}}) => updateItems(id, checked)}
              />
            ))}
          </>
        )}
      </div>
    </div>
  );
}

function getIdentifier(
  item: string | boolean | number | null | {id: string; key?: string}
): string | boolean | number | null {
  if (typeof item === 'object' && item !== null) {
    return item.key || item.id;
  }

  return item;
}
