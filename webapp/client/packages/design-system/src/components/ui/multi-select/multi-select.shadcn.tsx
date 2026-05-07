/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {ChevronDown, X} from 'lucide-react';

import {cn} from '../../../lib/utils';
import {Checkbox} from '../checkbox/checkbox.shadcn';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '../popover/popover.shadcn';

type Size = 'sm' | 'md' | 'lg';

const TRIGGER_SIZE_CLASS: Record<Size, string> = {
  sm: 'h-8 text-xs',
  md: 'h-9 text-sm',
  lg: 'h-10 text-base',
};

type MultiSelectProps<ItemType> = {
  id: string;
  items: ItemType[];
  itemToString?: (item: ItemType) => string;
  itemToElement?: (item: ItemType) => React.ReactNode;
  initialSelectedItems?: ItemType[];
  selectedItems?: ItemType[];
  label: React.ReactNode;
  titleText?: React.ReactNode;
  hideLabel?: boolean;
  helperText?: React.ReactNode;
  invalid?: boolean;
  invalidText?: React.ReactNode;
  disabled?: boolean;
  readOnly?: boolean;
  size?: Size;
  className?: string;
  open?: boolean;
  onMenuChange?: (open: boolean) => void;
  onChange?: (data: {selectedItems: ItemType[]}) => void;
  clearSelectionText?: string;
  clearSelectionDescription?: string;
};

function defaultItemToString<T>(item: T): string {
  if (item == null) return '';
  if (typeof item === 'object' && 'label' in (item as Record<string, unknown>)) {
    return String((item as Record<string, unknown>)['label'] ?? '');
  }
  return String(item);
}

function MultiSelect<ItemType>({
  id,
  items,
  itemToString = defaultItemToString,
  itemToElement,
  initialSelectedItems = [],
  selectedItems: controlledSelected,
  label,
  titleText,
  hideLabel,
  helperText,
  invalid,
  invalidText,
  disabled,
  readOnly,
  size = 'md',
  className,
  open: controlledOpen,
  onMenuChange,
  onChange,
  clearSelectionText = 'Clear selection',
  clearSelectionDescription = 'Total items selected:',
}: MultiSelectProps<ItemType>) {
  const isControlledSelected = controlledSelected !== undefined;
  const [uncontrolledSelected, setUncontrolledSelected] =
    React.useState<ItemType[]>(initialSelectedItems);
  const selectedItems = isControlledSelected
    ? (controlledSelected as ItemType[])
    : uncontrolledSelected;

  const isControlledOpen = controlledOpen !== undefined;
  const [uncontrolledOpen, setUncontrolledOpen] = React.useState(false);
  const open = isControlledOpen ? controlledOpen : uncontrolledOpen;

  const setOpen = (next: boolean) => {
    if (!isControlledOpen) setUncontrolledOpen(next);
    onMenuChange?.(next);
  };

  const setSelected = (next: ItemType[]) => {
    if (!isControlledSelected) setUncontrolledSelected(next);
    onChange?.({selectedItems: next});
  };

  const toggleItem = (item: ItemType) => {
    if (readOnly || disabled) return;
    const exists = selectedItems.some(
      (s) => itemToString(s) === itemToString(item),
    );
    const next = exists
      ? selectedItems.filter((s) => itemToString(s) !== itemToString(item))
      : [...selectedItems, item];
    setSelected(next);
  };

  const clearAll = (event: React.MouseEvent) => {
    event.stopPropagation();
    if (readOnly || disabled) return;
    setSelected([]);
  };

  const labelId = `${id}-label`;
  const helperId = `${id}-helper`;
  const errorId = `${id}-error`;

  const titleNode = titleText ?? label;

  return (
    <div data-slot="multi-select" className={cn('flex flex-col gap-1.5', className)}>
      {titleNode != null && (
        <label
          id={labelId}
          htmlFor={id}
          className={cn(
            'text-sm font-medium text-foreground',
            hideLabel && 'sr-only',
            disabled && 'opacity-50',
          )}
        >
          {titleNode}
        </label>
      )}

      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            id={id}
            type="button"
            disabled={disabled}
            aria-labelledby={titleNode != null ? labelId : undefined}
            aria-invalid={invalid || undefined}
            aria-describedby={
              invalid ? errorId : helperText ? helperId : undefined
            }
            data-slot="multi-select-trigger"
            data-readonly={readOnly || undefined}
            className={cn(
              'flex w-full items-center gap-2 rounded-md border border-input bg-transparent px-3 text-left shadow-xs outline-hidden transition-colors hover:border-ring focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-destructive/20 disabled:cursor-not-allowed disabled:opacity-50 data-[readonly=true]:cursor-default data-[readonly=true]:opacity-90',
              TRIGGER_SIZE_CLASS[size],
            )}
          >
            <span className="flex min-w-0 flex-1 items-center gap-2">
              {selectedItems.length > 0 && (
                <span
                  data-slot="multi-select-count"
                  className="inline-flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-primary px-1.5 text-xs font-medium text-primary-foreground"
                  aria-label={`${clearSelectionDescription} ${selectedItems.length}`}
                >
                  {selectedItems.length}
                </span>
              )}
              <span className="truncate text-muted-foreground">{label}</span>
            </span>
            {selectedItems.length > 0 && !readOnly && !disabled && (
              <span
                role="button"
                tabIndex={-1}
                aria-label={clearSelectionText}
                onClick={clearAll}
                className="ml-1 flex size-5 shrink-0 items-center justify-center rounded text-muted-foreground hover:text-foreground"
              >
                <X className="size-3.5" />
              </span>
            )}
            <ChevronDown
              className="size-4 shrink-0 text-muted-foreground"
              aria-hidden
            />
          </button>
        </PopoverTrigger>

        <PopoverContent
          align="start"
          sideOffset={4}
          className="w-(--radix-popover-trigger-width) min-w-56 p-0"
        >
          <ul role="listbox" aria-multiselectable className="max-h-72 overflow-auto py-1">
            {items.length === 0 ? (
              <li className="px-3 py-2 text-sm text-muted-foreground">
                No options
              </li>
            ) : (
              items.map((item, index) => {
                const itemKey = `${id}-item-${index}`;
                const isSelected = selectedItems.some(
                  (s) => itemToString(s) === itemToString(item),
                );
                return (
                  <li
                    key={itemKey}
                    role="option"
                    aria-selected={isSelected}
                    className="flex cursor-default items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground"
                    onClick={() => toggleItem(item)}
                  >
                    <Checkbox
                      checked={isSelected}
                      onCheckedChange={() => toggleItem(item)}
                      tabIndex={-1}
                      aria-label={itemToString(item)}
                      className="pointer-events-none"
                    />
                    <span className="min-w-0 flex-1 truncate">
                      {itemToElement ? itemToElement(item) : itemToString(item)}
                    </span>
                  </li>
                );
              })
            )}
          </ul>
        </PopoverContent>
      </Popover>

      {invalid && invalidText && (
        <p id={errorId} className="text-xs text-destructive">
          {invalidText}
        </p>
      )}
      {!invalid && helperText && (
        <p id={helperId} className="text-xs text-muted-foreground">
          {helperText}
        </p>
      )}
    </div>
  );
}

export {MultiSelect};
export type {MultiSelectProps};
