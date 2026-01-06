/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Add, TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import {
  Authorization,
  GeneralAuthorization,
  ResourceType,
  TaskAuthorization,
} from "src/utility/api/authorizations";
import { SearchResponse, usePagination } from "src/utility/api";
import useTranslate from "src/utility/localization";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal/useModal";
import { AddModal } from "./modals/add-modal";
import DeleteModal from "./modals/DeleteModal";
import { DataTableHeader } from "src/components/entityList/EntityList";

type AuthorizationListProps = {
  tab: ResourceType;
  data: SearchResponse<Authorization> | null;
  loading: boolean;
  reload: () => unknown;
  paginationProps: Omit<
    ReturnType<typeof usePagination>,
    "pageParams" | "resetPagination"
  >;
};

const AuthorizationList: FC<AuthorizationListProps> = ({
  tab,
  data,
  reload,
  paginationProps,
}) => {
  const { t } = useTranslate("authorizations");

  const [addAuthorization, addAuthorizationModal] = useEntityModal(
    AddModal,
    reload,
  );
  const [deleteAuthorization, deleteAuthorizationModal] = useEntityModal(
    DeleteModal,
    reload,
  );

  const propertyNameHeader: DataTableHeader<TaskAuthorization> = {
    header: t("resourcePropertyName"),
    key: "resourcePropertyName",
    isSortable: true,
  };

  const resourceIdHeader: DataTableHeader<GeneralAuthorization> = {
    header: t("resourceId"),
    key: "resourceId",
    isSortable: true,
  };

  const headers: DataTableHeader<Authorization>[] = [
    { header: t("ownerType"), key: "ownerType", isSortable: true },
    { header: t("ownerId"), key: "ownerId", isSortable: true },
    tab === ResourceType.USER_TASK ? propertyNameHeader : resourceIdHeader,
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
              icon: TrashCan,
              isDangerous: true,
              onClick: deleteAuthorization,
            },
          ]}
          maxDisplayCellLength={25}
          searchPlaceholder={t("searchByOwnerId")}
          searchKey="ownerId"
          {...paginationProps}
        />
      ) : (
        <C3EmptyState
          heading={t("noAuthorizationsYet", {
            tab: t(tab),
          })}
          description={t("authorizationDescription")}
          button={{
            label: t("createAuthorization"),
            onClick: () => {
              addAuthorization(tab);
            },
            icon: Add,
          }}
        />
      )}
      {addAuthorizationModal}
      {deleteAuthorizationModal}
    </>
  );
};

export default AuthorizationList;
