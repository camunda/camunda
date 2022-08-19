/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {PanelHeader} from 'modules/components/PanelHeader';
import {Container, Header, Tab} from './styled';

type TabType = {
  id: string;
  label: string;
  content: React.ReactNode;
};

type Props = {
  tabs: TabType[];
};

const TabView: React.FC<Props> = ({tabs = []}) => {
  const [selectedTab, setSelectedTab] = useState<TabType | null>(null);

  useEffect(() => {
    setSelectedTab(selectedTab ?? tabs[0] ?? null);
  }, [tabs, selectedTab]);

  return (
    <Container>
      {tabs.length === 1 ? (
        <>
          <PanelHeader title={tabs[0]!.label}></PanelHeader>
          {tabs[0]!.content}
        </>
      ) : (
        <>
          <Header>
            {tabs.map((tab) => (
              <Tab
                key={tab.id}
                isSelected={selectedTab?.id === tab.id}
                onClick={() => {
                  setSelectedTab(tab);
                }}
              >
                {tab.label}
              </Tab>
            ))}
          </Header>
          {selectedTab?.content}
        </>
      )}
    </Container>
  );
};

export {TabView};
