/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Pencil, Trash2 } from "@camunda/design-system/icons";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { formatDateTime } from "src/utility/format/provisioning";
import { isUserProvisioningEnabled } from "src/configuration";
import GroupStatus from "src/pages/groups/GroupStatusBadgeV2";
import { isGroupReadOnly } from "src/pages/groups/types";
import { usePagination } from "src/utility/api";
import { groupQueries } from "src/utility/api/groups/queries";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import EntityList from "src/components/entityListV2";
import { useNavigate } from "react-router-dom";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import useModal, { useEntityModal } from "src/components/modalV2/useModal";
import EditModal from "src/pages/groups/modalsV2/EditModal";
import DeleteModal from "src/pages/groups/modalsV2/DeleteModal";
import AddModal from "src/pages/groups/modalsV2/AddModal";
import PageEmptyState from "src/components/layoutV2/PageEmptyState";
import type { ProvisionedGroup } from "src/utility/api/groups";

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
  } = useQuery({
    ...groupQueries.search(pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((item) => ({
        ...item,
        id: item.groupId,
        status: isGroupReadOnly(item) ? <GroupStatus /> : null,
        lastModifiedAt: formatDateTime(item.lastModifiedAt),
      })),
    }),
  });

  const [addGroup, addModal] = useModal(AddModal, noop);
  const [updateGroup, editModal] = useEntityModal(EditModal, noop);
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, noop);
  const showDetails = ({ groupId }: ProvisionedGroup) => navigate(groupId);

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
          ...(isUserProvisioningEnabled
            ? [{ header: t("status"), key: "status" as const }]
            : []),
          {
            header: isUserProvisioningEnabled
              ? t("lastSynced")
              : t("lastModified"),
            key: "lastModifiedAt" as const,
          },
        ]}
        onEntityClick={showDetails}
        addEntityLabel={t("createGroup")}
        onAddEntity={addGroup}
        loading={loading}
        menuItems={[
          {
            label: t("edit"),
            icon: Pencil,
            onClick: updateGroup,
            disabled: isGroupReadOnly,
          },
          {
            label: t("delete"),
            icon: Trash2,
            isDangerous: true,
            onClick: deleteGroup,
            disabled: isGroupReadOnly,
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
