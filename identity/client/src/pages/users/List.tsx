/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate } from "react-router-dom";
import { Edit, TrashCan } from "@carbon/react/icons";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { formatDateTime } from "src/utility/format/provisioning";
import { isOIDC, isUserProvisioningEnabled } from "src/configuration";
import UserStatus from "src/pages/users/UserStatusTag";
import type { ProvisionedUser } from "src/pages/users/types";
import { usePagination } from "src/utility/api";
import { userQueries } from "src/utility/api/users/queries";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import InlineNotification, {
  TranslatedErrorInlineNotification,
} from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/users/modals/AddModal";
import EditModal from "src/pages/users/modals/EditModal";
import DeleteModal from "src/pages/users/modals/DeleteModal";
import PageEmptyState from "src/components/layout/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("users");
  const navigate = useNavigate();
  const { pageParams, page, search, ...paginationCallbacks } = usePagination();
  const {
    data: userSearchResults,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery(userQueries.search(pageParams));
  const tableData = userSearchResults?.items.map((user) => ({
    ...user,
    id: user.username,
    status: <UserStatus user={user} />,
    lastModifiedAt: formatDateTime(user.lastModifiedAt),
  }));
  const noop = () => {};
  const [addUser, addUserModal] = useModal(AddModal, noop);
  const [editUser, editUserModal] = useEntityModal(EditModal, noop);
  const [deleteUser, deleteUserModal] = useEntityModal(DeleteModal, noop);

  const showDetails = ({ username }: ProvisionedUser) =>
    navigate(`${username}`);

  const shouldShowEmptyState =
    !isOIDC && success && !search && !userSearchResults?.items.length;

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
      {isOIDC && (
        <InlineNotification
          kind="info"
          title={t("managedByIdentityProvider")}
        />
      )}
      <EntityList
        data={tableData}
        headers={[
          {
            header: t("username"),
            key: "username",
            isSortable: true,
          },
          { header: t("name"), key: "name", isSortable: true },
          { header: t("email"), key: "email", isSortable: true },
          { header: t("status"), key: "status" },
          {
            header: isUserProvisioningEnabled
              ? t("lastSynced")
              : t("lastModified"),
            key: "lastModifiedAt",
          },
        ]}
        menuItems={
          isOIDC
            ? undefined
            : [
                {
                  label: t("editUser"),
                  onClick: editUser,
                  icon: Edit,
                },
                {
                  label: t("delete"),
                  icon: TrashCan,
                  onClick: deleteUser,
                  isDangerous: true,
                },
              ]
        }
        onEntityClick={showDetails}
        addEntityLabel={isOIDC ? undefined : t("createUser")}
        onAddEntity={isOIDC ? undefined : addUser}
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
