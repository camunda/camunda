/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Pencil, Trash2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { roleQueries } from "src/utility/api/roles/queries";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import EntityList from "src/components/entityListV2";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import useModal, { useEntityModal } from "src/components/modalV2/useModal";
import AddModal from "src/pages/roles/modalsV2/AddModal";
import DeleteModal from "src/pages/roles/modalsV2/DeleteModal";
import EditModal from "src/pages/roles/modalsV2/EditModal";
import PageEmptyState from "src/components/layoutV2/PageEmptyState";
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
  } = useQuery({
    ...roleQueries.search(pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((role) => ({ ...role, id: role.roleId })),
    }),
  });

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
            icon: Pencil,
            onClick: editRole,
            disabled: ({ roleId }: Role) => defaultRoleIds.includes(roleId),
          },
          {
            label: t("delete"),
            icon: Trash2,
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
