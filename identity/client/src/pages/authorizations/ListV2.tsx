/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useMemo, useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Text,
} from "@camunda/design-system";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { authorizationQueries } from "src/utility/api/authorizations/queries";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import {
  ALL_RESOURCE_TYPES,
  RESOURCE_TYPES_WITHOUT_TENANT,
} from "src/utility/api/authorizations";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import AuthorizationList from "./AuthorizationsListV2";
import { Paths } from "src/components/global/routePaths";
import type {
  PermissionType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.10";

type ListProps = {
  isTenantsApiEnabled: boolean;
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  resourcePermissions: Record<ResourceType, PermissionType[]>;
  defaultRoleIds: string[];
};

const List: FC<ListProps> = ({
  isTenantsApiEnabled,
  isOIDC,
  isCamundaGroupsEnabled,
  resourcePermissions,
  defaultRoleIds,
}) => {
  const { t } = useTranslate("authorizations");
  const navigate = useNavigate();
  const { id } = useParams<{ id?: string }>();
  const authorizationTabs = isTenantsApiEnabled
    ? ALL_RESOURCE_TYPES
    : RESOURCE_TYPES_WITHOUT_TENANT;

  const [activeTab, setActiveTab] = useState<ResourceType>(() => {
    return authorizationTabs.find((tab) => tab === id) ?? authorizationTabs[0];
  });

  useEffect(() => {
    const routeTab = authorizationTabs.find((tab) => tab === id);
    const nextActiveTab = routeTab ?? authorizationTabs[0];

    setActiveTab(nextActiveTab);

    if (!routeTab) {
      void navigate(`${Paths.authorizations()}/${nextActiveTab}`, {
        replace: true,
      });
    }
  }, [authorizationTabs, id, navigate]);

  const { pageParams, page, resetPagination, ...paginationCallbacks } =
    usePagination();
  const {
    data,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery({
    ...authorizationQueries.search({
      ...pageParams,
      filter: { resourceType: activeTab },
    }),
    select: (data) => ({
      ...data,
      items: data.items.map((item) => ({
        ...item,
        id: item.authorizationKey,
      })),
    }),
  });
  const paginationProps = {
    page: { ...page, ...data?.page },
    ...paginationCallbacks,
  };

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
      <Tabs
        value={activeTab}
        onValueChange={(value) => {
          const newTab = value as ResourceType;
          resetPagination();
          setActiveTab(newTab);
          void navigate(`${Paths.authorizations()}/${newTab}`);
        }}
        orientation="vertical"
        className="gap-8"
      >
        <div className="flex flex-col gap-2">
          <Text as="p" variant="label-md" className="text-muted-foreground">
            {t("resourceType")}
          </Text>
          <TabsList aria-label={t("authorizationType")}>
            {authorizationTabs.map((tab) => (
              <TabsTrigger key={tab} value={tab}>
                {t(tab)}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>
        {authorizationTabs.map((tab) => (
          <TabsContent key={tab} value={tab}>
            <AuthorizationList
              tab={tab}
              data={transformedData}
              loading={loading}
              reload={reload}
              paginationProps={paginationProps}
              isOIDC={isOIDC}
              isCamundaGroupsEnabled={isCamundaGroupsEnabled}
              isTenantsApiEnabled={isTenantsApiEnabled}
              resourcePermissions={resourcePermissions}
              defaultRoleIds={defaultRoleIds}
            />
          </TabsContent>
        ))}
      </Tabs>
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("authorizationLoadError")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </Page>
  );
};

export default List;
