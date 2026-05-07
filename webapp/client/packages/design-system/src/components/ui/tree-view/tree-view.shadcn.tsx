/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {ChevronRight} from 'lucide-react';

import {cn} from '../../../lib/utils';

type TreeSize = 'xs' | 'sm';

type TreeContextValue = {
  size: TreeSize;
  active?: string | number;
  selected: Array<string | number>;
  multiselect: boolean;
  setActive: (id: string | number | undefined) => void;
  toggleSelected: (id: string | number) => void;
  registerNode: (id: string | number, ref: HTMLLIElement | null) => void;
  unregisterNode: (id: string | number) => void;
  visibleOrder: Array<string | number>;
};

const TreeContext = React.createContext<TreeContextValue | null>(null);

const TreeDepthContext = React.createContext<number>(0);

function useTreeContext() {
  const ctx = React.useContext(TreeContext);
  if (!ctx) throw new Error('TreeNode must be rendered inside TreeView');
  return ctx;
}

type TreeViewProps = {
  label: string;
  hideLabel?: boolean;
  size?: TreeSize;
  active?: string | number;
  selected?: Array<string | number>;
  multiselect?: boolean;
  onActivate?: (id: string | number | undefined) => void;
  onSelect?: (selected: Array<string | number>) => void;
  className?: string;
  children?: React.ReactNode;
} & Omit<
  React.HTMLAttributes<HTMLUListElement>,
  'onSelect' | 'children' | 'className'
>;

function TreeView({
  label,
  hideLabel,
  size = 'sm',
  active: controlledActive,
  selected: controlledSelected,
  multiselect = false,
  onActivate,
  onSelect,
  className,
  children,
  ...props
}: TreeViewProps) {
  const labelId = React.useId();
  const [uncontrolledActive, setUncontrolledActive] = React.useState<
    string | number | undefined
  >(undefined);
  const [uncontrolledSelected, setUncontrolledSelected] = React.useState<
    Array<string | number>
  >([]);

  const isControlledActive = controlledActive !== undefined;
  const isControlledSelected = controlledSelected !== undefined;

  const active = isControlledActive ? controlledActive : uncontrolledActive;
  const selected = isControlledSelected
    ? controlledSelected
    : uncontrolledSelected;

  const setActive = React.useCallback(
    (id: string | number | undefined) => {
      if (!isControlledActive) setUncontrolledActive(id);
      onActivate?.(id);
    },
    [isControlledActive, onActivate],
  );

  const toggleSelected = React.useCallback(
    (id: string | number) => {
      const next = multiselect
        ? selected.includes(id)
          ? selected.filter((x) => x !== id)
          : [...selected, id]
        : [id];
      if (!isControlledSelected) setUncontrolledSelected(next);
      onSelect?.(next);
    },
    [isControlledSelected, multiselect, onSelect, selected],
  );

  const nodeRefs = React.useRef<Map<string | number, HTMLLIElement>>(new Map());
  const [visibleOrder, setVisibleOrder] = React.useState<Array<string | number>>(
    [],
  );

  const recomputeOrder = React.useCallback(() => {
    setVisibleOrder([...nodeRefs.current.keys()]);
  }, []);

  const registerNode = React.useCallback(
    (id: string | number, ref: HTMLLIElement | null) => {
      if (ref) {
        nodeRefs.current.set(id, ref);
      } else {
        nodeRefs.current.delete(id);
      }
      recomputeOrder();
    },
    [recomputeOrder],
  );

  const unregisterNode = React.useCallback(
    (id: string | number) => {
      nodeRefs.current.delete(id);
      recomputeOrder();
    },
    [recomputeOrder],
  );

  return (
    <TreeContext.Provider
      value={{
        size,
        active,
        selected,
        multiselect,
        setActive,
        toggleSelected,
        registerNode,
        unregisterNode,
        visibleOrder,
      }}
    >
      {label && (
        <div
          id={labelId}
          className={cn(
            'mb-1 text-xs font-medium text-muted-foreground',
            hideLabel && 'sr-only',
          )}
        >
          {label}
        </div>
      )}
      <ul
        role="tree"
        aria-labelledby={labelId}
        aria-multiselectable={multiselect || undefined}
        data-slot="tree-view"
        data-size={size}
        className={cn('m-0 flex flex-col p-0', className)}
        {...props}
      >
        {children}
      </ul>
    </TreeContext.Provider>
  );
}

const SIZE_PADDING_CLASS: Record<TreeSize, string> = {
  xs: 'h-7 text-xs',
  sm: 'h-8 text-sm',
};

type TreeNodeProps = {
  id: string | number;
  label: React.ReactNode;
  value?: string;
  isExpanded?: boolean;
  defaultIsExpanded?: boolean;
  disabled?: boolean;
  href?: string;
  renderIcon?: React.ComponentType<{className?: string}>;
  onSelect?: (
    event: React.MouseEvent | React.KeyboardEvent,
    node: {id: string | number; label: React.ReactNode; value?: string},
  ) => void;
  onToggle?: (isExpanded: boolean) => void;
  className?: string;
  children?: React.ReactNode;
} & Omit<
  React.LiHTMLAttributes<HTMLLIElement>,
  'onSelect' | 'id' | 'children' | 'className'
>;

function TreeNode({
  id,
  label,
  value,
  isExpanded: controlledExpanded,
  defaultIsExpanded = false,
  disabled,
  href,
  renderIcon: Icon,
  onSelect,
  onToggle,
  className,
  children,
  ...props
}: TreeNodeProps) {
  const ctx = useTreeContext();
  const depth = React.useContext(TreeDepthContext);
  const liRef = React.useRef<HTMLLIElement | null>(null);

  const [uncontrolledExpanded, setUncontrolledExpanded] =
    React.useState(defaultIsExpanded);
  const isControlledExpanded = controlledExpanded !== undefined;
  const expanded = isControlledExpanded
    ? controlledExpanded
    : uncontrolledExpanded;

  const hasChildren = React.Children.count(children) > 0;
  const isActive = ctx.active === id;
  const isSelected = ctx.selected.includes(id);

  React.useEffect(() => {
    ctx.registerNode(id, liRef.current);
    return () => ctx.unregisterNode(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const setExpanded = (next: boolean) => {
    if (!isControlledExpanded) setUncontrolledExpanded(next);
    onToggle?.(next);
  };

  const handleClick = (event: React.MouseEvent) => {
    if (disabled) return;
    ctx.setActive(id);
    ctx.toggleSelected(id);
    onSelect?.(event, {id, label, value});
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLElement>) => {
    if (disabled) return;
    const order = ctx.visibleOrder;
    const idx = order.indexOf(id);
    switch (event.key) {
      case 'ArrowDown': {
        event.preventDefault();
        const next = order[idx + 1];
        if (next != null) {
          const el = document.querySelector<HTMLDivElement>(
            `[data-slot=tree-node-row][data-tree-id="${String(next)}"]`,
          );
          el?.focus();
        }
        break;
      }
      case 'ArrowUp': {
        event.preventDefault();
        const prev = order[idx - 1];
        if (prev != null) {
          const el = document.querySelector<HTMLDivElement>(
            `[data-slot=tree-node-row][data-tree-id="${String(prev)}"]`,
          );
          el?.focus();
        }
        break;
      }
      case 'ArrowRight':
        event.preventDefault();
        if (hasChildren && !expanded) setExpanded(true);
        break;
      case 'ArrowLeft':
        event.preventDefault();
        if (hasChildren && expanded) setExpanded(false);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        handleClick(event as unknown as React.MouseEvent);
        if (hasChildren) setExpanded(!expanded);
        break;
    }
  };

  const indent = `${depth * 1.25}rem`;

  const RowTag = href ? 'a' : 'div';

  return (
    <li
      role="treeitem"
      aria-expanded={hasChildren ? expanded : undefined}
      aria-selected={isSelected || undefined}
      aria-disabled={disabled || undefined}
      data-slot="tree-node"
      data-active={isActive || undefined}
      data-disabled={disabled || undefined}
      ref={liRef}
      className={cn('m-0 list-none', className)}
      {...props}
    >
      <RowTag
        href={href}
        data-slot="tree-node-row"
        data-tree-id={String(id)}
        tabIndex={disabled ? -1 : isActive || (depth === 0 && ctx.visibleOrder[0] === id) ? 0 : -1}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        style={{paddingLeft: indent}}
        className={cn(
          'flex items-center gap-1 px-2 outline-hidden transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:bg-accent data-[active=true]:bg-accent data-[active=true]:text-accent-foreground',
          SIZE_PADDING_CLASS[ctx.size],
          disabled && 'cursor-not-allowed opacity-50',
        )}
      >
        {hasChildren ? (
          <button
            type="button"
            aria-label={expanded ? 'Collapse' : 'Expand'}
            tabIndex={-1}
            onClick={(event) => {
              event.stopPropagation();
              setExpanded(!expanded);
            }}
            className="flex size-4 shrink-0 items-center justify-center text-muted-foreground"
          >
            <ChevronRight
              className={cn(
                'size-3 transition-transform',
                expanded && 'rotate-90',
              )}
            />
          </button>
        ) : (
          <span className="size-4 shrink-0" aria-hidden />
        )}
        {Icon && (
          <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        )}
        <span className="min-w-0 flex-1 truncate">{label}</span>
      </RowTag>
      {hasChildren && expanded && (
        <TreeDepthContext.Provider value={depth + 1}>
          <ul role="group" className="m-0 flex flex-col p-0">
            {children}
          </ul>
        </TreeDepthContext.Provider>
      )}
    </li>
  );
}

export {TreeView, TreeNode};
export type {TreeViewProps, TreeNodeProps, TreeSize};
