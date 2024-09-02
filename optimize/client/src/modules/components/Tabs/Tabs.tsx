/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, ComponentPropsWithoutRef, ReactNode} from 'react';
import {Tabs as CarbonTabs, TabList, Tab, TabPanels, TabPanel, TabsSkeleton} from '@carbon/react';

import {ignoreFragments} from 'services';

import './Tabs.scss';

interface TabsProps<T extends string | number>
  extends Omit<ComponentPropsWithoutRef<'div'>, 'onChange'> {
  value?: T;
  onChange?: (value: T) => void;
  showButtons?: boolean;
  isLoading?: boolean;
}

interface TabProps<T extends string | number>
  extends Omit<ComponentPropsWithoutRef<'div'>, 'title'> {
  value?: T;
  title?: ReactNode;
  disabled?: boolean;
}

export default function Tabs<T extends string | number>({
  value = 0 as T,
  onChange,
  children,
  showButtons = true,
  isLoading,
}: TabsProps<T>) {
  const tabs = ignoreFragments(children);
  const values = tabs.map<T>(({props: {value}}, idx) => (value || idx) as T);
  const [selected, setSelected] = useState<T>(value);

  useEffect(() => {
    setSelected(value);
  }, [value]);

  return (
    <CarbonTabs
      selectedIndex={getIndex(values, selected)}
      onChange={({selectedIndex}) => {
        const value = values[selectedIndex]!;
        onChange?.(value);
        setSelected(value);
      }}
    >
      {showButtons && !isLoading && (
        <TabList aria-label="tabs">
          {tabs
            .filter((tab) => !tab.props.hidden)
            .map(({props: {value, title, disabled, ...rest}}, idx) => (
              <Tab {...rest} key={getIndex(values, value || idx)} disabled={disabled}>
                {title}
              </Tab>
            ))}
        </TabList>
      )}
      {isLoading && <TabsSkeleton />}
      <TabPanels>{tabs}</TabPanels>
    </CarbonTabs>
  );
}

Tabs.Tab = ({children}: TabProps<number | string>): JSX.Element => {
  return <TabPanel>{children}</TabPanel>;
};

function getIndex<T extends string | number>(array: T[], value: T): number {
  const idx = array.indexOf(value);
  return idx !== -1 ? idx : 0;
}
