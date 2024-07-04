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
import { Role, searchRoles } from "src/utility/api/roles";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "./modals/AddModal";
import EditModal from "./modals/EditModal";
import { Edit, TrashCan } from "@carbon/react/icons";
import DeleteModal from "src/pages/roles/modals/DeleteModal";
import { C3EmptyState } from "@camunda/camunda-composite-components";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");
  const {
    data: rolesSearchResponse,
    loading,
    reload,
    success,
  } = useApi(searchRoles);
  const [addRole, addRoleModal] = useModal(AddModal, reload);
  const [editRole, editRoleModal] = useEntityModal(EditModal, reload);
  const [deleteRole, deleteRoleModal] = useEntityModal(DeleteModal, reload);
  const showDetails = ({ name }: Role) => navigate(`${name}`);

  if (success && !rolesSearchResponse?.items.length) {
    return (
      <Page>
        <PageTitle>
          <Translate>Users</Translate>
        </PageTitle>
        <C3EmptyState
          heading={t("You don’t have any roles yet")}
          description={t("Roles can be applied to a user.")}
          button={{
            label: t("Create a role"),
            onClick: addRole,
          }}
          link={{
            href: documentationHref("/concepts/access-control/roles", ""),
            label: t("Learn more about roles"),
          }}
        />
        {addRoleModal}
      </Page>
    );
  }

  return (
    <Page>
      <EntityList
        title={t("Roles")}
        data={rolesSearchResponse?.items || []}
        headers={[
          { header: t("Name"), key: "name" },
          { header: t("Description"), key: "description" },
        ]}
        menuItems={[
          {
            label: t("Edit"),
            icon: Edit,
            onClick: editRole,
          },
          {
            label: t("Delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteRole,
          },
        ]}
        sortProperty="name"
        onEntityClick={showDetails}
        addEntityLabel={t("Create role")}
        onAddEntity={addRole}
        onSearch={setSearch}
        loading={loading}
      />
      {success && (
        <DocumentationDescription>
          <Translate>Learn more about roles in our</Translate>{" "}
          <DocumentationLink path="/concepts/access-control/roles" />.
        </DocumentationDescription>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of roles could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
      {addRoleModal}
      {editRoleModal}
      {deleteRoleModal}
    </Page>
  );
};

export default List;
