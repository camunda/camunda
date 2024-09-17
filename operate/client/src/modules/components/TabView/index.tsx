/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PanelHeader} from 'modules/components/PanelHeader';
import {Container, Tab, Content, TabPanel, TabList} from './styled';
import {tracking} from 'modules/tracking';
import {Tabs, TabPanels, Stack} from '@carbon/react';

type TabType = {
  id: string;
  labelIcon?: React.ReactNode;
  label: string;
  content: React.ReactNode;
  removePadding?: boolean;
  testId?: string;
  onClick?: () => void;
};

type Props = {
  tabs: TabType[];
  eventName?: 'variables-panel-used';
  dataTestId?: string;
};

const TabView: React.FC<Props> = ({tabs = [], eventName, dataTestId}) => {
  return (
    <Container data-testid={dataTestId}>
      {tabs.length === 1 && tabs[0] !== undefined ? (
        <>
          <PanelHeader title={tabs[0].label} size="sm"></PanelHeader>
          <Content>{tabs[0].content}</Content>
        </>
      ) : (
        <Tabs>
          <TabList aria-label="Variable Panel Tabs">
            {tabs.map(({id, labelIcon, label, testId, onClick}) => (
              <Tab
                key={id}
                data-testid={testId}
                onClick={() => {
                  onClick?.();
                  if (eventName !== undefined) {
                    tracking.track({
                      eventName,
                      toTab: id,
                    });
                  }
                }}
              >
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
};

export {TabView};
