/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState, useMemo, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { TabsVertical, Tab, TabPanels } from "@carbon/react";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import {
  ALL_RESOURCE_TYPES,
  RESOURCE_TYPES_WITHOUT_TENANT,
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
import type { ResourceType } from "@camunda/camunda-api-zod-schemas/8.10";

const List: FC = () => {
  const { t } = useTranslate("authorizations");
  const authorizationTabs = isTenantsApiEnabled
    ? ALL_RESOURCE_TYPES
    : RESOURCE_TYPES_WITHOUT_TENANT;

  const [searchParams, setSearchParams] = useSearchParams();

  const [activeTab, setActiveTab] = useState<ResourceType>(() => {
    const param = searchParams.get("resourceType") as ResourceType;
    return param && authorizationTabs.includes(param) ? param : authorizationTabs[0];
  });

  const {
    data,
    loading,
    reload,
    success,
    resetPagination,
    ...paginationProps
  } = usePaginatedApi(searchAuthorization, {
    filter: { resourceType: activeTab },
  });

  const sortPermissionTypesAlphabetically = useCallback(
    (authorizationData: typeof data) => {
      return authorizationData
        ? {
            ...authorizationData,
            items: authorizationData.items?.map((item) => ({
              ...item,
              permissionTypes: [...item.permissionTypes].sort(),
            })),
          }
        : authorizationData;
    },
    [],
  );

  const transformedData = useMemo(
    () => sortPermissionTypesAlphabetically(data),
    [data, sortPermissionTypesAlphabetically],
  );

  return (
    <Page>
      <PageHeader
        title={t("authorizations")}
        linkText={t("authorizations").toLowerCase()}
        docsLinkPath="/components/concepts/access-control/authorizations/"
      />
      <TabsTitle>{t("resourceType")}</TabsTitle>
      <TabsContainer>
        <TabsVertical
          defaultSelectedIndex={authorizationTabs.indexOf(activeTab)}
          onChange={(tab: { selectedIndex: number }) => {
            const newTab = authorizationTabs[tab.selectedIndex];
            resetPagination();
            setActiveTab(newTab);
            setSearchParams({ resourceType: newTab }, { replace: true });
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
                  data={transformedData}
                  loading={loading}
                  reload={reload}
                  paginationProps={paginationProps}
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
