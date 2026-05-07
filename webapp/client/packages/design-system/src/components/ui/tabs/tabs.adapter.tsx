/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon pairs Tab/TabPanel by index; shadcn (Radix) pairs by `value`.
 * Adapter synthesises an index-based value (`tab-<n>`) shared between Tab
 * and TabPanel as a best-effort transition shim. Consumers wanting precise
 * pairing should pass an explicit `value` prop on both sides.
 */

import * as React from 'react';

import {
  Tabs as ShadcnTabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from './tabs.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  TabListProps as CarbonTabListProps,
  TabPanelProps as CarbonTabPanelProps,
  TabPanelsProps as CarbonTabPanelsProps,
  TabProps as CarbonTabProps,
  TabsProps as CarbonTabsProps,
} from '@carbon/react';

export type TabsProps = CarbonTabsProps;
export type TabListProps = CarbonTabListProps;
export type TabProps = CarbonTabProps;
export type TabPanelProps = CarbonTabPanelProps;
export type TabPanelsProps = CarbonTabPanelsProps;

const TabIndexContext = React.createContext<number>(0);

function indexValue(index: number): string {
  return `tab-${index}`;
}

function withSequentialIndex(children: React.ReactNode): React.ReactNode {
  let index = 0;
  return React.Children.map(children, (child) => {
    if (!React.isValidElement(child)) return child;
    const next = index;
    index += 1;
    return (
      <TabIndexContext.Provider value={next}>{child}</TabIndexContext.Provider>
    );
  });
}

function Tabs(props: TabsProps) {
  const {
    children,
    selectedIndex,
    defaultSelectedIndex,
    onChange,
    onTabCloseRequest,
    dismissable,
    selectionMode,
  } = props as TabsProps & {
    children?: React.ReactNode;
    selectedIndex?: number;
    defaultSelectedIndex?: number;
    onChange?: (data: {selectedIndex: number}) => void;
    onTabCloseRequest?: unknown;
    dismissable?: boolean;
    selectionMode?: string;
  };

  warnDroppedProps('Tabs', {
    onTabCloseRequest,
    dismissable,
    selectionMode,
  });

  const value =
    selectedIndex !== undefined ? indexValue(selectedIndex) : undefined;
  const defaultValue =
    defaultSelectedIndex !== undefined
      ? indexValue(defaultSelectedIndex)
      : indexValue(0);

  const handleValueChange = (next: string) => {
    if (!onChange) return;
    const match = /^tab-(\d+)$/.exec(next);
    if (!match || match[1] === undefined) return;
    onChange({selectedIndex: Number.parseInt(match[1], 10)});
  };

  return (
    <ShadcnTabs
      value={value}
      defaultValue={defaultValue}
      onValueChange={handleValueChange}
    >
      {children}
    </ShadcnTabs>
  );
}

function TabList(props: TabListProps) {
  const {
    children,
    className,
    'aria-label': ariaLabel,
    contained,
    iconSize,
    fullWidth,
    activation,
    light,
    leftOverflowButtonProps,
    rightOverflowButtonProps,
    scrollDebounceWait,
    scrollIntoView,
  } = props as TabListProps & {
    children?: React.ReactNode;
    className?: string;
    'aria-label'?: string;
    contained?: boolean;
    iconSize?: string;
    fullWidth?: boolean;
    activation?: string;
    light?: boolean;
    leftOverflowButtonProps?: unknown;
    rightOverflowButtonProps?: unknown;
    scrollDebounceWait?: number;
    scrollIntoView?: boolean;
  };

  warnDroppedProps('TabList', {
    contained,
    iconSize,
    fullWidth,
    activation,
    light,
    leftOverflowButtonProps,
    rightOverflowButtonProps,
    scrollDebounceWait,
    scrollIntoView,
  });

  return (
    <TabsList aria-label={ariaLabel} className={className}>
      {withSequentialIndex(children)}
    </TabsList>
  );
}

function Tab(props: TabProps) {
  const {
    children,
    className,
    disabled,
    onClick,
    renderIcon: RenderIcon,
    secondaryLabel,
    onKeyDown,
    title,
  } = props as TabProps & {
    children?: React.ReactNode;
    className?: string;
    disabled?: boolean;
    onClick?: React.MouseEventHandler<HTMLButtonElement>;
    renderIcon?: React.ElementType;
    secondaryLabel?: React.ReactNode;
    onKeyDown?: React.KeyboardEventHandler<HTMLButtonElement>;
    title?: string;
  };

  const index = React.useContext(TabIndexContext);

  warnDroppedProps('Tab', {secondaryLabel});

  return (
    <TabsTrigger
      value={indexValue(index)}
      disabled={disabled}
      onClick={onClick}
      onKeyDown={onKeyDown}
      title={title}
      className={className}
    >
      {RenderIcon ? <RenderIcon /> : null}
      {children}
    </TabsTrigger>
  );
}

function TabPanels(props: TabPanelsProps) {
  const {children} = props as TabPanelsProps & {children?: React.ReactNode};
  return <>{withSequentialIndex(children)}</>;
}

function TabPanel(props: TabPanelProps) {
  const {children, className} = props as TabPanelProps & {
    children?: React.ReactNode;
    className?: string;
  };
  const index = React.useContext(TabIndexContext);
  return (
    <TabsContent value={indexValue(index)} className={className}>
      {children}
    </TabsContent>
  );
}

export {Tab, TabList, TabPanel, TabPanels, Tabs};
