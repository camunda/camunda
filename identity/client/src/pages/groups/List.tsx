/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { Edit, TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { documentationHref } from "src/components/documentation";
import { Group, searchGroups } from "src/utility/api/groups";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import EditModal from "src/pages/groups/modals/EditModal";
import DeleteModal from "src/pages/groups/modals/DeleteModal";
import AddModal from "src/pages/groups/modals/AddModal";

const List: FC = () => {
  const { t } = useTranslate();
  const navigate = useNavigate();

  const {
    data: groupSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchGroups);
  const [addGroup, addModal] = useModal(AddModal, reload);
  const [updateGroup, editModal] = useEntityModal(EditModal, reload);
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, reload);
  const showDetails = ({ groupKey }: Group) => navigate(`${groupKey}`);

  const pageHeader = (
    <PageHeader title="Groups" linkText="groups" linkUrl="/concepts/groups/" />
  );

  if (success && !groupSearchResults?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("You don’t have any groups yet")}
          description={t(
            "Roles, permissions, and authorizations can be applied to a group, and any users added to the group will inherit them. Utilizing groups can enhance the efficiency of managing users over time.",
          )}
          button={{
            label: t("Create a group"),
            onClick: addGroup,
          }}
          link={{
            href: documentationHref("/concepts/access-control/groups", ""),
            label: t("Learn more about groups"),
          }}
        />
        {addModal}
      </Page>
    );
  }
  return (
    <Page>
      {pageHeader}
      <EntityList
        data={groupSearchResults == null ? [] : groupSearchResults.items}
        headers={[{ header: t("name"), key: "name" }]}
        sortProperty="name"
        addEntityLabel={t("Create group")}
        onAddEntity={addGroup}
        menuItems={[
          { label: t("Edit"), icon: Edit, onClick: updateGroup },
          {
            label: t("Delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteGroup,
          },
        ]}
        onEntityClick={showDetails}
        loading={loading}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of groups could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}

      <>
        {addModal}
        {editModal}
        {deleteModal}
      </>
    </Page>
  );
};

export default List;
