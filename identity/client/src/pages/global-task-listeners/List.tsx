/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback } from "react";
import { Edit, TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import {
  GlobalTaskListener,
  searchGlobalTaskListeners,
} from "src/utility/api/global-task-listeners";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/global-task-listeners/modals/AddModal";
import EditModal from "src/pages/global-task-listeners/modals/EditModal";
import DeleteModal from "src/pages/global-task-listeners/modals/DeleteModal";
import PageEmptyState from "src/components/layout/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("globalTaskListeners");

  const {
    data: globalTaskListeners,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchGlobalTaskListeners);

  const [addGlobalTaskListener, addGlobalTaskListenerModal] = useModal(
    AddModal,
    reload,
  );
  const [editGlobalTaskListener, editGlobalTaskListenerModal] = useEntityModal(
    EditModal,
    reload,
  );

  const getExecutionOrderDisplay = useCallback(
    (afterNonGlobal: boolean) => {
      return afterNonGlobal
        ? t("executionOrderAfter")
        : t("executionOrderBefore");
    },
    [t],
  );

  const shouldShowEmptyState =
    success && !search && !globalTaskListeners?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("globalTaskListeners")}
      linkText={t("globalTaskListeners").toLowerCase()}
      docsLinkPath="/docs/components/concepts/global-user-task-listeners/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"globalTaskListener"}
          docsLinkPath="/docs/components/concepts/global-user-task-listeners/"
          handleClick={addGlobalTaskListener}
        />
        {addGlobalTaskListenerModal}
      </Page>
    );
  }

  // Transform data to include display-friendly execution order and event types
  const transformedData = globalTaskListeners?.items.map((listener) => ({
    ...listener,
    executionOrderDisplay: getExecutionOrderDisplay(listener.afterNonGlobal),
    eventTypesDisplay: listener.eventTypes.includes("all")
      ? t("eventTypeAll")
      : listener.eventTypes.join(", "),
  }));

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={transformedData ?? []}
        headers={[
          {
            header: t("globalTaskListenerId"),
            key: "id",
            isSortable: true,
          },
          { header: t("listenerType"), key: "type", isSortable: true },
          {
            header: t("eventType"),
            key: "eventTypesDisplay",
            isSortable: true,
          },
          { header: t("retries"), key: "retries", isSortable: true },
          {
            header: t("executionOrder"),
            key: "executionOrderDisplay",
            isSortable: true,
          },
          { header: t("priority"), key: "priority", isSortable: true },
        ]}
        addEntityLabel={t("createListener")}
        onAddEntity={addGlobalTaskListener}
        loading={loading}
        menuItems={[
          {
            label: t("editGlobalTaskListener"),
            icon: Edit,
            onClick: (entity) =>
              editGlobalTaskListener(entity as unknown as GlobalTaskListener),
          },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: (entity) =>
              deleteGlobalTaskListener(entity as unknown as GlobalTaskListener),
          },
        ]}
        searchPlaceholder={t("searchById")}
        searchKey="id"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("globalTaskListenersCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addGlobalTaskListenerModal}
      {editGlobalTaskListenerModal}
      {deleteGlobalTaskListenerModal}
    </Page>
  );
};

export default List;
