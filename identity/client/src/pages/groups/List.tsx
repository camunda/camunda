/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Edit, TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { useSearchGroups } from "src/utility/api/groups/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { useNavigate } from "react-router-dom";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import EditModal from "src/pages/groups/modals/EditModal";
import DeleteModal from "src/pages/groups/modals/DeleteModal";
import AddModal from "src/pages/groups/modals/AddModal";
import PageEmptyState from "src/components/layout/PageEmptyState";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

const List: FC = () => {
  const { t } = useTranslate("groups");
  const navigate = useNavigate();
  const noop = () => {};

  const { pageParams, page, search, ...paginationCallbacks } = usePagination();
  const {
    data: groupSearchResults,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useSearchGroups(pageParams);

  const [addGroup, addModal] = useModal(AddModal, noop);
  const [updateGroup, editModal] = useEntityModal(EditModal, noop);
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, noop);
  const showDetails = ({ groupId }: Group) => navigate(groupId);

  const shouldShowEmptyState =
    success && !search && !groupSearchResults?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("groups")}
      linkText={t("groups").toLowerCase()}
      docsLinkPath="/components/admin/group/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"group"}
          docsLinkPath="/components/admin/group/"
          handleClick={addGroup}
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
        page={{ ...page, ...groupSearchResults?.page }}
        {...paginationCallbacks}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("groupsListCouldNotLoad")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
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
