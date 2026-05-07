/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Dropdown` is a monolithic single-select listbox driven by an
 * `items` array, `selectedItem`, and `onChange({selectedItem})`. shadcn's
 * `Select` is compound (Trigger / Content / Item) and value-driven by string
 * keys. The adapter owns the items→items rendering and bridges the selection
 * callback shape.
 */

import * as React from 'react';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../select/select.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {DropdownProps as CarbonDropdownProps} from '@carbon/react';

export type DropdownProps<ItemType> = CarbonDropdownProps<ItemType>;

type ItemLike = {id?: string; label?: React.ReactNode} & Record<string, unknown>;

function getItemKey(item: unknown, index: number): string {
  if (item && typeof item === 'object') {
    const id = (item as ItemLike).id;
    if (typeof id === 'string') return id;
  }
  if (typeof item === 'string') return item;
  return String(index);
}

function getItemLabel(
  item: unknown,
  itemToString?: (item: unknown) => string,
): React.ReactNode {
  if (itemToString) return itemToString(item);
  if (item && typeof item === 'object') {
    const label = (item as ItemLike).label;
    if (label !== undefined) return label as React.ReactNode;
  }
  if (typeof item === 'string') return item;
  return null;
}

function Dropdown<ItemType>(props: DropdownProps<ItemType>) {
  const {
    'aria-label': ariaLabel,
    ariaLabel: ariaLabelDeprecated,
    autoAlign,
    className,
    decorator,
    direction,
    disabled,
    downshiftProps,
    helperText,
    hideLabel,
    id,
    initialSelectedItem,
    invalid,
    invalidText,
    itemToElement,
    itemToString,
    items,
    label,
    light,
    onChange,
    readOnly,
    renderSelectedItem,
    selectedItem,
    size,
    slug,
    titleText,
    translateWithId,
    type,
    warn,
    warnText,
  } = props;

  warnDroppedProps('Dropdown', {
    ariaLabel: ariaLabelDeprecated,
    autoAlign,
    decorator,
    direction,
    downshiftProps,
    helperText,
    hideLabel,
    initialSelectedItem,
    invalid,
    invalidText,
    itemToElement,
    light,
    readOnly,
    renderSelectedItem,
    size,
    slug,
    translateWithId,
    type,
    warn,
    warnText,
  });

  const itemKeys = React.useMemo(
    () => items.map((item, idx) => getItemKey(item, idx)),
    [items],
  );

  const selectedKey = React.useMemo(() => {
    if (selectedItem === undefined || selectedItem === null) return undefined;
    const idx = items.indexOf(selectedItem);
    if (idx === -1) return undefined;
    return itemKeys[idx];
  }, [items, itemKeys, selectedItem]);

  const handleValueChange = (value: string) => {
    if (!onChange) return;
    const idx = itemKeys.indexOf(value);
    const next = idx === -1 ? null : (items[idx] ?? null);
    onChange({selectedItem: next});
  };

  const placeholder =
    typeof label === 'string' ? label : (label as React.ReactNode);

  return (
    <div className={className}>
      {titleText !== undefined ? (
        <label htmlFor={id} className="mb-1 block text-sm font-medium">
          {titleText}
        </label>
      ) : null}
      <Select
        value={selectedKey ?? ''}
        onValueChange={handleValueChange}
        disabled={disabled}
      >
        <SelectTrigger
          id={id}
          aria-label={ariaLabel}
          aria-invalid={invalid || undefined}
        >
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {items.map((item, idx) => {
            const key = itemKeys[idx] ?? String(idx);
            return (
              <SelectItem key={key} value={key}>
                {getItemLabel(
                  item,
                  itemToString as unknown as
                    | ((item: unknown) => string)
                    | undefined,
                )}
              </SelectItem>
            );
          })}
        </SelectContent>
      </Select>
    </div>
  );
}

export {Dropdown};
