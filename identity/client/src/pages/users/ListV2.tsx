/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate } from "react-router-dom";
import { Pencil, Trash2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { userQueries } from "src/utility/api/users/queries";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import EntityList from "src/components/entityListV2";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import useModal, { useEntityModal } from "src/components/modalV2/useModal";
import AddModal from "src/pages/users/modalsV2/AddModal";
import EditModal from "src/pages/users/modalsV2/EditModal";
import DeleteModal from "src/pages/users/modalsV2/DeleteModal";
import PageEmptyState from "src/components/layoutV2/PageEmptyState";
import type { User } from "@camunda/camunda-api-zod-schemas/8.10";

const List: FC = () => {
  const { t } = useTranslate("users");
  const navigate = useNavigate();
  const { pageParams, page, search, ...paginationCallbacks } = usePagination();
  const {
    data: userSearchResults,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery({
    ...userQueries.search(pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((user) => ({ ...user, id: user.username })),
    }),
  });
  const noop = () => {};
  const [addUser, addUserModal] = useModal(AddModal, noop);
  const [editUser, editUserModal] = useEntityModal(EditModal, noop);
  const [deleteUser, deleteUserModal] = useEntityModal(DeleteModal, noop);

  const showDetails = ({ username }: User) => navigate(`${username}`);

  const shouldShowEmptyState =
    success && !search && userSearchResults.items.length === 0;

  const pageHeader = (
    <PageHeader
      title={t("users")}
      linkText={t("users").toLowerCase()}
      docsLinkPath="/components/admin/user/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"user"}
          docsLinkPath="/components/admin/user/"
          handleClick={addUser}
        />
        {addUserModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={userSearchResults?.items}
        headers={[
          {
            header: t("username"),
            key: "username",
            isSortable: true,
          },
          { header: t("name"), key: "name", isSortable: true },
          { header: t("email"), key: "email", isSortable: true },
        ]}
        menuItems={[
          {
            label: t("editUser"),
            onClick: editUser,
            icon: Pencil,
          },
          {
            label: t("delete"),
            icon: Trash2,
            onClick: deleteUser,
            isDangerous: true,
          },
        ]}
        onEntityClick={showDetails}
        addEntityLabel={t("createUser")}
        onAddEntity={addUser}
        loading={loading}
        searchPlaceholder={t("searchByUsername")}
        searchKey="username"
        page={{ ...page, ...userSearchResults?.page }}
        {...paginationCallbacks}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("listUsersLoadError")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
      {addUserModal}
      {editUserModal}
      {deleteUserModal}
    </Page>
  );
};

export default List;
