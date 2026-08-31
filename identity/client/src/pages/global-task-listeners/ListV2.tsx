/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Pencil, Trash2 } from "lucide-react";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { useQuery } from "@tanstack/react-query";
import { globalTaskListenerQueries } from "src/utility/api/global-task-listeners/queries";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import EntityList from "src/components/entityListV2";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import useModal, { useEntityModal } from "src/components/modalV2/useModal";
import AddModal from "src/pages/global-task-listeners/modalsV2/AddModal";
import EditModal from "src/pages/global-task-listeners/modalsV2/EditModal";
import DeleteModal from "src/pages/global-task-listeners/modalsV2/DeleteModal";
import PageEmptyState from "src/components/layoutV2/PageEmptyState";
import { getEventTypeLabels } from "src/pages/global-task-listeners/utility";

const List: FC = () => {
  const { t } = useTranslate("globalTaskListeners");
  const noop = () => {};

  const { pageParams, page, search, ...paginationCallbacks } = usePagination();
  const {
    data: globalTaskListeners,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery(globalTaskListenerQueries.search(pageParams));

  const [addGlobalTaskListener, addGlobalTaskListenerModal] = useModal(
    AddModal,
    noop,
  );
  const [editGlobalTaskListener, editGlobalTaskListenerModal] = useEntityModal(
    EditModal,
    noop,
  );
  const [deleteGlobalTaskListener, deleteGlobalTaskListenerModal] =
    useEntityModal(DeleteModal, noop);

  const shouldShowEmptyState =
    success && !search && globalTaskListeners.items.length === 0;

  const pageHeader = (
    <PageHeader
      title={t("globalTaskListeners")}
      linkText={t("globalTaskListeners").toLowerCase()}
      docsLinkPath="/components/concepts/global-user-task-listeners/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"globalTaskListener"}
          docsLinkPath="/components/concepts/global-user-task-listeners/"
          handleClick={addGlobalTaskListener}
        />
        {addGlobalTaskListenerModal}
      </Page>
    );
  }

  const transformedData = globalTaskListeners?.items.map((listener) => ({
    ...listener,
    afterNonGlobal: listener.afterNonGlobal
      ? t("executionOrderAfter")
      : t("executionOrderBefore"),
    eventTypes: getEventTypeLabels(listener.eventTypes, t),
    originalListener: listener,
  }));

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={transformedData}
        headers={[
          {
            header: t("globalTaskListenerId"),
            key: "id",
            isSortable: true,
          },
          { header: t("listenerType"), key: "type", isSortable: true },
          {
            header: t("eventType"),
            key: "eventTypes",
            isSortable: false,
          },
          { header: t("retries"), key: "retries", isSortable: false },
          {
            header: t("executionOrder"),
            key: "afterNonGlobal",
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
            icon: Pencil,
            onClick: (entity) =>
              editGlobalTaskListener(entity.originalListener),
          },
          {
            label: t("delete"),
            icon: Trash2,
            isDangerous: true,
            onClick: (entity) =>
              deleteGlobalTaskListener(entity.originalListener),
          },
        ]}
        searchPlaceholder={t("searchById")}
        searchKey="id"
        page={{ ...page, ...globalTaskListeners?.page }}
        {...paginationCallbacks}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("globalTaskListenersCouldNotLoad")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
      {addGlobalTaskListenerModal}
      {editGlobalTaskListenerModal}
      {deleteGlobalTaskListenerModal}
    </Page>
  );
};

export default List;
