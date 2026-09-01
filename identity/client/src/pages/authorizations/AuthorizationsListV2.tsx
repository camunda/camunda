/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button, EmptyState } from "@camunda/design-system";
import { SearchResponse, usePagination } from "src/utility/api";
import useTranslate from "src/utility/localization";
import EntityList from "src/components/entityListV2";
import { useEntityModal } from "src/components/modalV2/useModal";
import { AddModal } from "./modalsV2/add-modal";
import DeleteModal from "./modalsV2/DeleteModal";
import { DataTableHeader } from "src/components/entityListV2/EntityList";
import type {
  Authorization,
  PermissionType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.10";

type AuthorizationWithId = Authorization & { id: string };

type AuthorizationListProps = {
  tab: ResourceType;
  data: SearchResponse<AuthorizationWithId> | null | undefined;
  loading: boolean;
  reload: () => unknown;
  paginationProps: Omit<
    ReturnType<typeof usePagination>,
    "pageParams" | "resetPagination"
  >;
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  isTenantsApiEnabled: boolean;
  resourcePermissions: Record<ResourceType, PermissionType[]>;
  defaultRoleIds: string[];
};

const AuthorizationList: FC<AuthorizationListProps> = ({
  tab,
  data,
  reload,
  paginationProps,
  isOIDC,
  isCamundaGroupsEnabled,
  isTenantsApiEnabled,
  resourcePermissions,
  defaultRoleIds,
}) => {
  const { t } = useTranslate("authorizations");

  const [addAuthorization, addAuthorizationModal] = useEntityModal(
    AddModal,
    reload,
    {
      isOIDC,
      isCamundaGroupsEnabled,
      isTenantsApiEnabled,
      resourcePermissions,
    },
  );
  const [deleteAuthorization, deleteAuthorizationModal] = useEntityModal(
    DeleteModal,
    reload,
  );

  const propertyNameHeader: DataTableHeader<AuthorizationWithId> = {
    header: t("resourcePropertyName"),
    key: "resourcePropertyName",
    isSortable: true,
  };

  const resourceIdHeader: DataTableHeader<AuthorizationWithId> = {
    header: t("resourceId"),
    key: "resourceId",
    isSortable: true,
  };

  const headers: DataTableHeader<AuthorizationWithId>[] = [
    { header: t("ownerType"), key: "ownerType", isSortable: true },
    { header: t("ownerId"), key: "ownerId", isSortable: true },
    tab === "USER_TASK" ? propertyNameHeader : resourceIdHeader,
    { header: t("permissionTypes"), key: "permissionTypes" },
  ];

  return (
    <>
      {data?.items?.length || paginationProps.search ? (
        <EntityList
          title={t(tab)}
          data={data?.items}
          headers={headers}
          addEntityLabel={t("createAuthorization")}
          onAddEntity={() => {
            addAuthorization(tab);
          }}
          menuItems={[
            {
              label: t("delete"),
              icon: Trash2,
              isDangerous: true,
              onClick: deleteAuthorization,
              disabled: ({ ownerType, ownerId }) =>
                ownerType === "ROLE" && defaultRoleIds.includes(ownerId),
            },
          ]}
          maxDisplayCellLength={25}
          searchPlaceholder={t("searchByOwnerId")}
          searchKey="ownerId"
          {...paginationProps}
        />
      ) : (
        <EmptyState
          heading={t("noAuthorizationsYet", {
            tab: t(tab),
          })}
          description={t("authorizationDescription")}
          action={
            <Button
              onClick={() => {
                addAuthorization(tab);
              }}
            >
              <Plus data-icon="inline-start" aria-hidden="true" />
              {t("createAuthorization")}
            </Button>
          }
        />
      )}
      {addAuthorizationModal}
      {deleteAuthorizationModal}
    </>
  );
};

export default AuthorizationList;
