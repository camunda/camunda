/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { useNavigate } from "react-router";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { documentationHref } from "src/components/documentation";
import { searchRoles, Role } from "src/utility/api/roles";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/roles/modals/AddModal";
import DeleteModal from "src/pages/roles/modals/DeleteModal";

const List: FC = () => {
  const { t } = useTranslate("roles");
  const navigate = useNavigate();
  const { data: roles, loading, reload, success } = useApi(searchRoles);

  const [addRole, addRoleModal] = useModal(AddModal, reload);
  const [deleteRole, deleteRoleModal] = useEntityModal(DeleteModal, reload);

  const showDetails = ({ roleKey }: Role) => navigate(roleKey);

  const pageHeader = (
    <PageHeader
      title={t("roles")}
      linkText="roles"
      linkUrl="/concepts/roles/"
    />
  );

  if (success && !roles?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("noRoles")}
          description={t("createNewRoleToStart")}
          button={{
            label: t("createRole"),
            onClick: addRole,
          }}
          link={{
            href: documentationHref("/concepts/roles/", ""),
            label: t("learnMoreAboutRoles"),
          }}
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
          { header: t("roleId"), key: "roleKey" },
          { header: t("roleName"), key: "name" },
        ]}
        sortProperty="name"
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
