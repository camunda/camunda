/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Edit, TrashCan } from "@carbon/react/icons";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { roleQueries } from "src/utility/api/roles/queries";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/roles/modals/AddModal";
import DeleteModal from "src/pages/roles/modals/DeleteModal";
import EditModal from "src/pages/roles/modals/EditModal";
import PageEmptyState from "src/components/layout/PageEmptyState";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

type ListProps = {
  defaultRoleIds: string[];
};

const List: FC<ListProps> = ({ defaultRoleIds }) => {
  const { t } = useTranslate("roles");
  const navigate = useNavigate();
  const noop = () => {};

  const { pageParams, page, search, ...paginationCallbacks } = usePagination();
  const {
    data: roles,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery(roleQueries.search(pageParams));

  const [addRole, addRoleModal] = useModal(AddModal, noop);
  const [editRole, editRoleModal] = useEntityModal(EditModal, noop);
  const [deleteRole, deleteRoleModal] = useEntityModal(DeleteModal, noop);

  const showDetails = ({ roleId }: Role) => navigate(roleId);

  const shouldShowEmptyState = success && !search && !roles?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("roles")}
      linkText={t("roles").toLowerCase()}
      docsLinkPath="/components/admin/role/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"role"}
          docsLinkPath="/components/admin/role/"
          handleClick={addRole}
        />
        {addRoleModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={roles?.items ?? []}
        headers={[
          { header: t("roleId"), key: "roleId", isSortable: true },
          { header: t("roleName"), key: "name", isSortable: true },
        ]}
        onEntityClick={showDetails}
        addEntityLabel={t("createRole")}
        onAddEntity={addRole}
        loading={loading}
        menuItems={[
          {
            label: t("editRole"),
            icon: Edit,
            onClick: editRole,
            disabled: ({ roleId }: Role) => defaultRoleIds.includes(roleId),
          },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteRole,
            disabled: ({ roleId }: Role) => defaultRoleIds.includes(roleId),
          },
        ]}
        searchPlaceholder={t("searchByRoleId")}
        searchKey="roleId"
        page={{ ...page, ...roles?.page }}
        {...paginationCallbacks}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("rolesCouldNotLoad")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
      {addRoleModal}
      {editRoleModal}
      {deleteRoleModal}
    </Page>
  );
};

export default List;
