/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, ComponentPropsWithoutRef, ReactNode, Children} from 'react';
import {Tabs, TabList, Tab, TabPanels, TabPanel} from '@carbon/react';

import {isReactElement} from 'services';

interface CarbonTabsProps<T extends string | number>
  extends Omit<ComponentPropsWithoutRef<'div'>, 'onChange'> {
  value?: T;
  onChange?: (value: T) => void;
  showButtons?: boolean;
}

interface CarbonTabProps<T extends string | number>
  extends Omit<ComponentPropsWithoutRef<'div'>, 'title'> {
  value: T;
  title?: ReactNode;
  disabled?: boolean;
}

export default function CarbonTabs<T extends string | number>({
  value = 0 as T,
  onChange,
  children,
  showButtons = true,
}: CarbonTabsProps<T>) {
  const tabs = Children.toArray(children).filter(isReactElement<CarbonTabProps<T>>);
  const values = tabs.map<T>(({props: {value}}) => value);
  const [selected, setSelected] = useState<T>(value);

  useEffect(() => {
    setSelected(value);
  }, [value]);

  return (
    <Tabs
      selectedIndex={getIndex(values, selected)}
      onChange={({selectedIndex}) => {
        const value = values[selectedIndex]!;
        onChange?.(value);
        setSelected(value);
      }}
    >
      {showButtons && (
        <TabList aria-label="tabs">
          {tabs.map(({props: {value, title, disabled}}) => (
            <Tab key={getIndex(values, value)} disabled={disabled}>
              {title}
            </Tab>
          ))}
        </TabList>
      )}
      <TabPanels>{tabs}</TabPanels>
    </Tabs>
  );
}

CarbonTabs.Tab = ({children}: CarbonTabProps<number | string>): JSX.Element => {
  return <TabPanel>{children}</TabPanel>;
};

function getIndex<T extends string | number>(array: T[], value: T): number {
  const idx = array.indexOf(value);
  return idx !== -1 ? idx : 0;
}
