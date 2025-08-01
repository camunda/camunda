/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { Edit, TrashCan, Add } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
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
  const { t } = useTranslate("groups");
  const navigate = useNavigate();

  const {
    data: groupSearchResults,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchGroups);

  const [addGroup, addModal] = useModal(AddModal, reload);
  const [updateGroup, editModal] = useEntityModal(EditModal, reload);
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, reload);
  const showDetails = ({ groupId }: Group) => navigate(groupId);

  const shouldShowEmptyState =
    success && !search && !groupSearchResults?.items.length;

  const pageHeader = (
    <PageHeader
      title="Groups"
      linkText="groups"
      linkUrl=""
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("noGroupsCreated")}
          description={t("startByCreatingGroup")}
          button={{
            label: t("createAGroup"),
            onClick: addGroup,
            icon: Add,
          }}
          link={{
            href: documentationHref("https://docs.camunda.io/", ""),
            label: t("learnMoreAboutGroups"),
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
        headers={[
          { header: t("groupId"), key: "groupId", isSortable: true },
          { header: t("groupName"), key: "name", isSortable: true },
        ]}
        onEntityClick={showDetails}
        addEntityLabel={t("createGroup")}
        onAddEntity={addGroup}
        loading={loading}
        menuItems={[
          { label: t("edit"), icon: Edit, onClick: updateGroup },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteGroup,
          },
        ]}
        searchPlaceholder={t("searchByGroupId")}
        searchKey="groupId"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("groupsListCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
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
