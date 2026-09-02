/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useId, useMemo, useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Combobox,
  Label,
  PageContentLayout,
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
  const comboboxId = useId();
  const availableResourceTypes = isTenantsApiEnabled
    ? ALL_RESOURCE_TYPES
    : RESOURCE_TYPES_WITHOUT_TENANT;
  const authorizationTypeOptions = useMemo(
    () =>
      availableResourceTypes.map((resourceType) => ({
        label: t(resourceType),
        value: resourceType,
      })),
    [availableResourceTypes, t],
  );

  const [selectedResourceType, setSelectedResourceType] =
    useState<ResourceType>(() => {
      return (
        availableResourceTypes.find((resourceType) => resourceType === id) ??
        availableResourceTypes[0]
      );
    });

  useEffect(() => {
    const routeResourceType = availableResourceTypes.find(
      (resourceType) => resourceType === id,
    );
    const nextResourceType = routeResourceType ?? availableResourceTypes[0];

    setSelectedResourceType(nextResourceType);

    if (!routeResourceType) {
      void navigate(`${Paths.authorizations()}/${nextResourceType}`, {
        replace: true,
      });
    }
  }, [availableResourceTypes, id, navigate]);

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
      filter: { resourceType: selectedResourceType },
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
      <PageContentLayout
        sidebarPosition="left"
        sidebarWidth={240}
        sidebar={
          <Card>
            <CardHeader>
              <CardTitle>{t("filters")}</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-1.5">
              <Label htmlFor={comboboxId}>{t("authorizationType")}</Label>
              <Combobox
                id={comboboxId}
                className="w-full"
                options={authorizationTypeOptions}
                value={selectedResourceType}
                onValueChange={(value) => {
                  if (!value) {
                    return;
                  }

                  const newResourceType = value as ResourceType;
                  resetPagination();
                  setSelectedResourceType(newResourceType);
                  void navigate(`${Paths.authorizations()}/${newResourceType}`);
                }}
              />
            </CardContent>
          </Card>
        }
      >
        <AuthorizationList
          resourceType={selectedResourceType}
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
      </PageContentLayout>
    </Page>
  );
};

export default List;
