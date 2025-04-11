/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ReactNode, useEffect } from "react";
import {
  Tab,
  TabList,
  TabPanel as CarbonTabPanel,
  TabPanels,
  Tabs as CarbonTabs,
} from "@carbon/react";
import useTranslate from "src/utility/localization";
import { useNavigate } from "react-router";
import styled from "styled-components";

export type TabsProps<
  T extends { key: string; label: string; content: ReactNode },
> = {
  path: string;
  tabs: readonly T[];
  selectedTabKey: string;
};

const TabPanel = styled(CarbonTabPanel)`
  padding-left: 0;
  padding-right: 0;
`;

const Tabs = <T extends { key: string; label: string; content: ReactNode }>({
  path,
  selectedTabKey,
  tabs,
}: TabsProps<T>) => {
  const navigate = useNavigate();
  const { t } = useTranslate("components");

  const selectedTabIndex = tabs.findIndex(({ key }) => key === selectedTabKey);

  useEffect(() => {
    if (selectedTabIndex === -1) {
      void navigate(`${path}/${tabs[0].key}`, { replace: true });
    }
  }, [navigate, path, selectedTabIndex]);

  return (
    <CarbonTabs
      selectedIndex={selectedTabIndex > -1 ? selectedTabIndex : 0}
      onChange={({ selectedIndex }: { selectedIndex: number }) =>
        navigate(`${path}/${tabs[selectedIndex].key}`)
      }
    >
      <TabList aria-label={t("Tabs")}>
        {tabs.map(({ key, label }) => (
          <Tab key={`tab-${key}`}>{label}</Tab>
        ))}
      </TabList>
      <TabPanels>
        {tabs.map(({ key, content }) => (
          <TabPanel key={`tab-panel-${key}`}>{content}</TabPanel>
        ))}
      </TabPanels>
    </CarbonTabs>
  );
};

export default Tabs;
