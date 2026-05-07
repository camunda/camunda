/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from './combo-box.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {ComboBoxProps as CarbonComboBoxProps} from '@carbon/react';

export type ComboBoxProps<ItemType> = CarbonComboBoxProps<ItemType>;

type CarbonOnChange<ItemType> = (data: {
  selectedItem: ItemType | null | undefined;
  inputValue?: string | null;
}) => void;

function defaultItemToString<ItemType>(item: ItemType | null): string {
  if (item == null) return '';
  if (typeof item === 'string') return item;
  if (typeof item === 'object' && item !== null && 'label' in item) {
    const label = (item as {label?: unknown}).label;
    return typeof label === 'string' ? label : String(label ?? '');
  }
  return String(item);
}

function ComboBox<ItemType>(props: ComboBoxProps<ItemType>) {
  const {
    id,
    items,
    itemToString,
    selectedItem,
    initialSelectedItem,
    onChange,
    placeholder,
    disabled,
    className,
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    light,
    size,
    titleText,
    readOnly,
    direction,
    autoAlign,
    allowCustomValue,
    typeahead,
    decorator,
    slug,
    ariaLabel,
    'aria-label': ariaLabelHyphen,
    onInputChange,
    onToggleClick,
    shouldFilterItem,
    itemToElement,
    downshiftProps,
    downshiftActions,
    inputProps,
    translateWithId,
  } = props as ComboBoxProps<ItemType> & {
    id?: string;
    items: ItemType[];
    itemToString?: (item: ItemType | null) => string;
    selectedItem?: ItemType | null;
    initialSelectedItem?: ItemType;
    onChange?: CarbonOnChange<ItemType>;
    placeholder?: string;
    disabled?: boolean;
    className?: string;
    helperText?: React.ReactNode;
    invalid?: boolean;
    invalidText?: React.ReactNode;
    warn?: boolean;
    warnText?: React.ReactNode;
    light?: boolean;
    size?: string;
    titleText?: React.ReactNode;
    readOnly?: boolean;
    direction?: string;
    autoAlign?: boolean;
    allowCustomValue?: boolean;
    typeahead?: boolean;
    decorator?: React.ReactNode;
    slug?: React.ReactNode;
    ariaLabel?: string;
    'aria-label'?: string;
    onInputChange?: (input: string) => void;
    onToggleClick?: (evt: React.MouseEvent<HTMLButtonElement>) => void;
    shouldFilterItem?: unknown;
    itemToElement?: unknown;
    downshiftProps?: unknown;
    downshiftActions?: unknown;
    inputProps?: unknown;
    translateWithId?: unknown;
  };

  warnDroppedProps('ComboBox', {
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    light,
    size,
    readOnly,
    direction,
    autoAlign,
    allowCustomValue,
    typeahead,
    decorator,
    slug,
    onToggleClick,
    shouldFilterItem,
    itemToElement,
    downshiftProps,
    downshiftActions,
    inputProps,
    translateWithId,
  });

  const toString = itemToString ?? defaultItemToString;

  const stringItems = items.map((item) => toString(item));

  const handleValueChange = (next: string | null) => {
    const value = next ?? '';
    onInputChange?.(value);
    if (!onChange) return;
    const match = items.find((item) => toString(item) === value);
    onChange({selectedItem: match ?? null, inputValue: value});
  };

  return (
    <div className="flex flex-col gap-1">
      {titleText !== undefined ? (
        <label htmlFor={id} className="text-sm font-medium">
          {titleText}
        </label>
      ) : null}
      <Combobox
        items={stringItems}
        itemToStringLabel={(item: string) => item}
        defaultValue={
          initialSelectedItem != null ? toString(initialSelectedItem) : undefined
        }
        value={selectedItem != null ? toString(selectedItem) : undefined}
        onValueChange={handleValueChange}
        disabled={disabled}
      >
        <ComboboxInput
          id={id}
          placeholder={placeholder}
          disabled={disabled}
          aria-label={ariaLabelHyphen ?? ariaLabel}
          className={className}
        />
        <ComboboxContent>
          <ComboboxList>
            {items.map((item, index) => {
              const label = toString(item);
              return (
                <ComboboxItem key={`${label}-${index}`} value={label}>
                  {label}
                </ComboboxItem>
              );
            })}
          </ComboboxList>
        </ComboboxContent>
      </Combobox>
    </div>
  );
}

export {ComboBox};
