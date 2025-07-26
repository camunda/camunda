/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { TabsVertical, Tab, TabPanels } from "@carbon/react";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import {
  ResourceType,
  searchAuthorization,
} from "src/utility/api/authorizations";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import {
  CustomTabListVertical,
  CustomTabPanel,
  TabsContainer,
  TabsTitle,
} from "./components";
import AuthorizationList from "./AuthorizationsList";
import { isTenantsApiEnabled } from "src/configuration";

const List: FC = () => {
  const { t } = useTranslate("authorizations");

  const allResourceTypes = Object.values(ResourceType);
  const authorizationTabs = isTenantsApiEnabled
    ? allResourceTypes
    : allResourceTypes.filter((type) => type !== ResourceType.TENANT);

  const [activeTab, setActiveTab] = useState<string>(authorizationTabs[0]);
  const { data, loading, reload, success } = useApi(searchAuthorization, {
    filter: { resourceType: activeTab },
  });

  return (
    <Page>
      <PageHeader title="Authorizations" linkText="authorizations" linkUrl="" />
      <TabsTitle>{t("resourceType")}</TabsTitle>
      <TabsContainer>
        <TabsVertical
          onChange={(tab: { selectedIndex: number }) => {
            setActiveTab(authorizationTabs[tab.selectedIndex]);
          }}
        >
          <CustomTabListVertical aria-label={t("authorizationType")}>
            {authorizationTabs.map((tab) => (
              <Tab key={tab}>{t(tab)}</Tab>
            ))}
          </CustomTabListVertical>
          <TabPanels>
            {authorizationTabs.map((tab) => (
              <CustomTabPanel key={tab}>
                <AuthorizationList
                  tab={tab}
                  data={data}
                  loading={loading}
                  reload={reload}
                />
              </CustomTabPanel>
            ))}
          </TabPanels>
        </TabsVertical>
      </TabsContainer>
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("authorizationLoadError")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
