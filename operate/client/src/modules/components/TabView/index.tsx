/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {PanelHeader} from 'modules/components/PanelHeader';
import {Container, Tab, Content, TabPanel, TabList} from './styled';
import {tracking} from 'modules/tracking';
import {Tabs, TabPanels, Stack} from '@carbon/react';

type TabType<TabId extends string = string> = {
  id: TabId;
  labelIcon?: React.ReactNode;
  label: string;
  content: React.ReactNode;
  removePadding?: boolean;
  testId?: string;
  onClick?: () => void;
};

type Props<TabId extends string = string> = {
  tabs: TabType<TabId>[];
  eventName?: 'variables-panel-used';
  dataTestId?: string;
  /**
   * Id of the tab that should be selected. Left unset (or pointing at a
   * tab that no longer exists), defaults to the first tab.
   */
  activeTabId?: TabId;
  onTabChange?: (id: TabId) => void;
};

function TabView<TabId extends string = string>({
  tabs = [],
  eventName,
  dataTestId,
  activeTabId,
  onTabChange,
}: Props<TabId>) {
  // Falls back to internal state so TabView still behaves as an
  // uncontrolled component when the caller doesn't pass `activeTabId`.
  const [internalActiveTabId, setInternalActiveTabId] = useState<
    TabId | undefined
  >(undefined);
  const effectiveActiveTabId = activeTabId ?? internalActiveTabId;

  const selectedIndex =
    effectiveActiveTabId === undefined
      ? 0
      : Math.max(
          0,
          tabs.findIndex(({id}) => id === effectiveActiveTabId),
        );

  return (
    <Container data-testid={dataTestId}>
      {tabs.length === 1 && tabs[0] !== undefined ? (
        <>
          <PanelHeader title={tabs[0].label} size="sm"></PanelHeader>
          <Content>{tabs[0].content}</Content>
        </>
      ) : (
        <Tabs
          selectedIndex={selectedIndex}
          onChange={({selectedIndex: newIndex}) => {
            const tab = tabs[newIndex];
            if (tab === undefined) {
              return;
            }
            tab.onClick?.();
            setInternalActiveTabId(tab.id);
            onTabChange?.(tab.id);
            if (eventName !== undefined) {
              tracking.track({
                eventName,
                toTab: tab.id,
              });
            }
          }}
        >
          <TabList aria-label="Variable Panel Tabs">
            {tabs.map(({id, labelIcon, label, testId}) => (
              <Tab key={id} data-testid={testId}>
                <Stack orientation="horizontal" gap={3}>
                  {label}
                  {labelIcon}
                </Stack>
              </Tab>
            ))}
          </TabList>
          <TabPanels>
            {tabs.map(({id, content, removePadding = false}) => (
              <TabPanel key={id} $removePadding={removePadding}>
                {content}
              </TabPanel>
            ))}
          </TabPanels>
        </Tabs>
      )}
    </Container>
  );
}

export {TabView};
