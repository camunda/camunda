/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {PanelHeader} from 'modules/components/Carbon/PanelHeader';
import {Container, Tab, Content} from './styled';
import {tracking} from 'modules/tracking';
import {Tabs, TabList, TabPanels, TabPanel} from '@carbon/react';

type TabType = {
  id: string;
  label: string;
  content: React.ReactNode;
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
            {tabs.map(({id, label}) => (
              <Tab
                key={id}
                onClick={() => {
                  if (eventName !== undefined) {
                    tracking.track({
                      eventName,
                      toTab: id,
                    });
                  }
                }}
              >
                {label}
              </Tab>
            ))}
          </TabList>
          <TabPanels>
            {tabs.map(({id, content}) => (
              <TabPanel key={id}>{content}</TabPanel>
            ))}
          </TabPanels>
        </Tabs>
      )}
    </Container>
  );
};

export {TabView};
