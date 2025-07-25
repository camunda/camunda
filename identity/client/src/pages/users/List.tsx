/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate } from "react-router";
import { Add, Edit, TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import {
  documentationHref,
  DocumentationLink,
} from "src/components/documentation";
import { searchUser, User } from "src/utility/api/users";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/users/modals/AddModal";
import EditModal from "src/pages/users/modals/EditModal";
import DeleteModal from "src/pages/users/modals/DeleteModal";

const List: FC = () => {
  const { t } = useTranslate("users");
  const navigate = useNavigate();
  const {
    data: userSearchResults,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchUser);
  const [addUser, addUserModal] = useModal(AddModal, reload);
  const [editUser, editUserModal] = useEntityModal(EditModal, reload);
  const [deleteUser, deleteUserModal] = useEntityModal(DeleteModal, reload);

  const showDetails = ({ username }: User) => navigate(`${username}`);

  const pageHeader = (
    <PageHeader title={t("users")} linkText="users" linkUrl="" />
  );

  if (success && !search && !userSearchResults?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("noUsersCreatedYet")}
          description={
            <>
              {t("startBy")}{" "}
              <DocumentationLink path="">
                {t("creatingANewUser")}
              </DocumentationLink>{" "}
              {t("toGetStarted")}
            </>
          }
          button={{
            label: t("createAUser"),
            onClick: addUser,
            icon: Add,
          }}
          link={{
            href: documentationHref("https://docs.camunda.io/", ""),
            label: t("learnMoreAboutUsers"),
          }}
        />
        {addUserModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={userSearchResults == null ? [] : userSearchResults.items}
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
            icon: Edit,
          },
          {
            label: t("delete"),
            icon: TrashCan,
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
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("listUsersLoadError")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addUserModal}
      {editUserModal}
      {deleteUserModal}
    </Page>
  );
};

export default List;
