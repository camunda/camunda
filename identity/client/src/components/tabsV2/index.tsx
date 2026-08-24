/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ReactNode, useEffect } from "react";
import {
  Tabs as DSTabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { useNavigate } from "react-router-dom";

export type TabsProps<
  T extends { key: string; label: string; content: ReactNode },
> = {
  path: string;
  tabs: readonly T[];
  selectedTabKey: string;
};

const Tabs = <T extends { key: string; label: string; content: ReactNode }>({
  path,
  selectedTabKey,
  tabs,
}: TabsProps<T>) => {
  const navigate = useNavigate();
  const { t } = useTranslate("components");

  const selectedTabIndex = tabs.findIndex(({ key }) => key === selectedTabKey);
  const activeTabKey = selectedTabIndex > -1 ? selectedTabKey : tabs[0].key;

  useEffect(() => {
    if (selectedTabIndex === -1) {
      void navigate(`${path}/${tabs[0].key}`, { replace: true });
    }
  }, [navigate, path, selectedTabIndex, tabs]);

  return (
    <DSTabs
      value={activeTabKey}
      onValueChange={(key) => {
        void navigate(`${path}/${key}`);
      }}
    >
      <TabsList variant="line" aria-label={t("Tabs")}>
        {tabs.map(({ key, label }) => (
          <TabsTrigger key={`tab-${key}`} value={key}>
            {label}
          </TabsTrigger>
        ))}
      </TabsList>
      {tabs.map(({ key, content }) => (
        <TabsContent key={`tab-panel-${key}`} value={key}>
          {content}
        </TabsContent>
      ))}
    </DSTabs>
  );
};

export default Tabs;
