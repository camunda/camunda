/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  createContext,
  FC,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
} from "react";
import {
  Tabs as DSTabs,
  TabsContent as DSTabsContent,
  TabsList,
  TabsTrigger,
} from "@camunda/design-system";
import { useNavigate } from "react-router-dom";
import useTranslate from "src/utility/localization";

export type TabItem = {
  key: string;
  label: string;
  content: ReactNode;
};

type TabsContextValue = {
  tabs: readonly TabItem[];
  activeTabKey: string;
};

const TabsContext = createContext<TabsContextValue | null>(null);

const useTabsContext = () => {
  const context = useContext(TabsContext);
  if (!context) {
    throw new Error(
      "Tabs.* components must be rendered within a <Tabs> from src/components/tabsV2",
    );
  }
  return context;
};

export type TabsProps = {
  path: string;
  tabs: readonly TabItem[];
  selectedTabKey: string;
  children: ReactNode;
};

/**
 * Compound root: owns tab-in-URL navigation and provides tab state to
 * `TabsHeaderList` and `TabsContent`, so those can be placed independently
 * (e.g. `TabsHeaderList` inside a `PageHeader`'s `tabs` slot, `TabsContent`
 * below it) instead of being rendered as one fixed block.
 */
export const Tabs: FC<TabsProps> = ({
  path,
  tabs,
  selectedTabKey,
  children,
}) => {
  const navigate = useNavigate();

  const selectedTabIndex = tabs.findIndex(({ key }) => key === selectedTabKey);
  const activeTabKey = selectedTabIndex > -1 ? selectedTabKey : tabs[0].key;

  useEffect(() => {
    if (selectedTabIndex === -1) {
      void navigate(`${path}/${tabs[0].key}`, { replace: true });
    }
  }, [navigate, path, selectedTabIndex, tabs]);

  const contextValue = useMemo(
    () => ({ tabs, activeTabKey }),
    [tabs, activeTabKey],
  );

  return (
    <TabsContext.Provider value={contextValue}>
      <DSTabs
        value={activeTabKey}
        onValueChange={(key) => void navigate(`${path}/${key}`)}
      >
        {children}
      </DSTabs>
    </TabsContext.Provider>
  );
};

export const TabsHeaderList: FC = () => {
  const { tabs } = useTabsContext();
  const { t } = useTranslate("components");

  return (
    <TabsList variant="line" aria-label={t("Tabs")}>
      {tabs.map(({ key, label }) => (
        <TabsTrigger key={key} value={key}>
          {label}
        </TabsTrigger>
      ))}
    </TabsList>
  );
};

export const TabsContent: FC = () => {
  const { tabs } = useTabsContext();

  return tabs.map(({ key, content }) => (
    <DSTabsContent key={key} value={key}>
      {content}
    </DSTabsContent>
  ));
};
