/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useState,
  ComponentPropsWithoutRef,
  ReactNode,
  createContext,
  useContext,
  cloneElement,
  ReactElement,
} from 'react';
import {Tabs as CarbonTabs, TabList, Tab, TabPanels, TabPanel, TabsSkeleton} from '@carbon/react';

import {ignoreFragments} from 'services';

import './Tabs.scss';

type TabValue = number | string;

const TabsContext = createContext<{
  alreadyOpened: TabValue[];
}>({
  alreadyOpened: [],
});

const useTabsContext = () => {
  const context = useContext(TabsContext);
  if (!context) {
    throw new Error('useTabsContext must be used within a Tabs component');
  }
  return context;
};

interface TabsProps<T extends TabValue> extends Omit<ComponentPropsWithoutRef<'div'>, 'onChange'> {
  value?: T;
  onChange?: (value: T) => void;
  showButtons?: boolean;
  isLoading?: boolean;
}

interface TabProps<T extends TabValue> extends Omit<ComponentPropsWithoutRef<'div'>, 'title'> {
  value?: T;
  title?: ReactNode;
  disabled?: boolean;
}

export default function Tabs<T extends TabValue>({
  value,
  onChange,
  children,
  showButtons = true,
  isLoading,
}: TabsProps<T>) {
  const tabs = ignoreFragments<TabProps<T>>(children);
  const values = tabs.map<T>(({props: {value}}, idx) => value ?? (idx as T));
  const initialValue = value ?? values[0] ?? 0;
  const [selectedIndex, setSelectedIndex] = useState<number>(getIndex(values, initialValue));
  const [alreadyOpened, setAlreadyOpened] = useState<TabValue[]>([initialValue]);

  function handleTabChange({selectedIndex}: {selectedIndex: number}) {
    const newValue = values[selectedIndex];

    if (newValue === undefined) {
      return;
    }

    onChange?.(newValue);
    setSelectedIndex(selectedIndex);

    if (!alreadyOpened.includes(newValue)) {
      setAlreadyOpened((prev) => [...prev, newValue]);
    }
  }

  return (
    <TabsContext.Provider value={{alreadyOpened}}>
      <CarbonTabs selectedIndex={selectedIndex} onChange={handleTabChange}>
        {showButtons && !isLoading && <TabList aria-label="tabs">{renderTabs(tabs)}</TabList>}
        {isLoading && <TabsSkeleton />}
        <TabPanels>{renderTabPanels(tabs)}</TabPanels>
      </CarbonTabs>
    </TabsContext.Provider>
  );
}

Tabs.Tab = function Tab({children, value}: TabProps<TabValue>): JSX.Element {
  const {alreadyOpened} = useTabsContext();
  const shouldRender = value !== undefined && alreadyOpened.includes(value);
  return <TabPanel>{shouldRender && children}</TabPanel>;
};

function getIndex<T extends TabValue>(array: T[], value: T): number {
  const idx = array.indexOf(value);
  return idx !== -1 ? idx : 0;
}

function renderTabs<T extends TabValue>(tabs: ReactElement<TabProps<T>>[]) {
  return tabs
    .filter((tab) => !tab.props.hidden)
    .map(({props: {value, title, disabled, ...rest}}, idx) => (
      <Tab {...rest} key={idx} disabled={disabled}>
        {title}
      </Tab>
    ));
}

function renderTabPanels<T extends TabValue>(tabs: ReactElement<TabProps<T>>[]) {
  return tabs.map((tab, idx) => {
    const value = tab.props.value ?? (idx as T);
    const key = 'tab-' + value;
    return cloneElement(tab, {
      key,
      value,
    });
  });
}
