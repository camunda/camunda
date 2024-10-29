/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageTitle } from "src/components/layout/Page";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import {
  documentationHref,
  DocumentationLink,
} from "src/components/documentation";
import { searchUser, User } from "src/utility/api/users";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/users/modals/AddModal";
import EditModal from "src/pages/users/modals/EditModal";
import DeleteModal from "src/pages/users/modals/DeleteModal";
import { Edit, TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");
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

  if (success && !userSearchResults?.items.length) {
    return (
      <Page>
        <PageTitle>
          <Translate>Users</Translate>
        </PageTitle>
        <C3EmptyState
          heading={t("You don’t have any users yet")}
          description={t(
            "Roles, permissions, and authorizations can be applied to a user.",
          )}
          button={{
            label: t("Create a user"),
            onClick: addUser,
          }}
          link={{
            href: documentationHref("/concepts/access-control/users", ""),
            label: t("Learn more about groups"),
          }}
        />
        {addUserModal}
      </Page>
    );
  }

  return (
    <Page>
      <EntityList
        title={t("Users")}
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
            label: t("Delete user"),
            icon: TrashCan,
            onClick: deleteUser,
            isDangerous: true,
          },
        ]}
        sortProperty="username"
        onEntityClick={showDetails}
        addEntityLabel={t("Create user")}
        onAddEntity={addUser}
        onSearch={setSearch}
        loading={loading}
      />
      {success && (
        <DocumentationDescription>
          <Translate>Learn more about users in our</Translate>{" "}
          <DocumentationLink path="/concepts/access-control/users" />.
        </DocumentationDescription>
      )}
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
