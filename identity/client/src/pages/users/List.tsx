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
import { useApi } from "src/utility/api/hooks";
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
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const {
    data: userSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchUser);
  const [addUser, addUserModal] = useModal(AddModal, reload);
  const [editUser, editUserModal] = useEntityModal(EditModal, reload);
  const [deleteUser, deleteUserModal] = useEntityModal(DeleteModal, reload);

  const showDetails = ({ username }: User) => navigate(`${username}`);

  const pageHeader = (
    <PageHeader title="Users" linkText="users" linkUrl="/concepts/users/" />
  );

  if (success && !userSearchResults?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("No users created yet")}
          description={
            <>
              <Translate>Start by</Translate>{" "}
              <DocumentationLink path="/concepts/access-control/users">
                creating a new user
              </DocumentationLink>{" "}
              <Translate>to get started</Translate>
            </>
          }
          button={{
            label: t("Create a user"),
            onClick: addUser,
            icon: Add,
          }}
          link={{
            href: documentationHref("/concepts/access-control/users", ""),
            label: t("Learn more about users"),
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
          { header: t("Username"), key: "username" },
          { header: t("Name"), key: "name" },
          { header: t("Email"), key: "email" },
        ]}
        menuItems={[
          {
            label: t("Edit user"),
            onClick: editUser,
            icon: Edit,
          },
          {
            label: t("Delete"),
            icon: TrashCan,
            onClick: deleteUser,
            isDangerous: true,
          },
        ]}
        sortProperty="username"
        onEntityClick={showDetails}
        addEntityLabel={t("Create user")}
        onAddEntity={addUser}
        loading={loading}
        searchPlaceholder={t("Search by username")}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of users could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
      {addUserModal}
      {editUserModal}
      {deleteUserModal}
    </Page>
  );
};

export default List;
