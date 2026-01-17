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
  searchTaskListeners,
  TaskListener,
} from "src/utility/api/task-listeners";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/task-listeners/modals/AddModal";
import EditModal from "src/pages/task-listeners/modals/EditModal";
import DeleteModal from "src/pages/task-listeners/modals/DeleteModal";
import PageEmptyState from "src/components/layout/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("taskListeners");

  const {
    data: taskListeners,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchTaskListeners);

  const [addTaskListener, addTaskListenerModal] = useModal(AddModal, reload);
  const [editTaskListener, editTaskListenerModal] = useEntityModal(
    EditModal,
    reload,
  );
  const [deleteTaskListener, deleteTaskListenerModal] = useEntityModal(
    DeleteModal,
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
    success && !search && !taskListeners?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("globalUserTaskListeners")}
      linkText={t("globalUserTaskListeners").toLowerCase()}
      docsLinkPath="/docs/components/concepts/task-listeners/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"taskListener"}
          docsLinkPath="/docs/components/concepts/task-listeners/"
          handleClick={addTaskListener}
        />
        {addTaskListenerModal}
      </Page>
    );
  }

  // Transform data to include display-friendly execution order and event types
  const transformedData = taskListeners?.items.map((listener) => ({
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
            header: t("taskListenerId"),
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
        onAddEntity={addTaskListener}
        loading={loading}
        menuItems={[
          {
            label: t("editTaskListener"),
            icon: Edit,
            onClick: (entity) =>
              editTaskListener(entity as unknown as TaskListener),
          },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: (entity) =>
              deleteTaskListener(entity as unknown as TaskListener),
          },
        ]}
        searchPlaceholder={t("searchById")}
        searchKey="id"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("taskListenersCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addTaskListenerModal}
      {editTaskListenerModal}
      {deleteTaskListenerModal}
    </Page>
  );
};

export default List;
