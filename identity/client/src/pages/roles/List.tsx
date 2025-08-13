/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import { useNavigate } from "react-router";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { searchRoles, Role } from "src/utility/api/roles";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/roles/modals/AddModal";
import DeleteModal from "src/pages/roles/modals/DeleteModal";
import PageEmptyState from "src/components/layout/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("roles");
  const RESOURCE_TYPE_STRING = t("role").toLowerCase();
  const navigate = useNavigate();
  const {
    data: roles,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchRoles);

  const [addRole, addRoleModal] = useModal(AddModal, reload);
  const [deleteRole, deleteRoleModal] = useEntityModal(DeleteModal, reload);

  const showDetails = ({ roleId }: Role) => navigate(roleId);

  const shouldShowEmptyState = success && !search && !roles?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("roles")}
      linkText={t("roles").toLowerCase()}
      docsLinkPath=""
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceType={RESOURCE_TYPE_STRING}
          docsLinkPath=""
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
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteRole,
          },
        ]}
        searchPlaceholder={t("searchByRoleId")}
        searchKey="roleId"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of roles could not be loaded."
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addRoleModal}
      {deleteRoleModal}
    </Page>
  );
};

export default List;
