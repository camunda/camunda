/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Tag } from "@carbon/react";
import { Edit, TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { searchGlobalExecutionListeners } from "src/utility/api/global-execution-listeners";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/global-execution-listeners/modals/AddModal";
import EditModal from "src/pages/global-execution-listeners/modals/EditModal";
import DeleteModal from "src/pages/global-execution-listeners/modals/DeleteModal";
import PageEmptyState from "src/components/layout/PageEmptyState";
import { getEventTypeLabels } from "src/pages/global-execution-listeners/utility";

const List: FC = () => {
  const { t } = useTranslate("globalExecutionListeners");

  const {
    data: globalExecutionListeners,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchGlobalExecutionListeners);

  const [addGlobalExecutionListener, addGlobalExecutionListenerModal] =
    useModal(AddModal, reload);
  const [editGlobalExecutionListener, editGlobalExecutionListenerModal] =
    useEntityModal(EditModal, reload);
  const [deleteGlobalExecutionListener, deleteGlobalExecutionListenerModal] =
    useEntityModal(DeleteModal, reload);

  const shouldShowEmptyState =
    success && !search && !globalExecutionListeners?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("globalExecutionListeners")}
      linkText={t("globalExecutionListeners").toLowerCase()}
      docsLinkPath="/docs/components/concepts/global-execution-listeners/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"globalExecutionListener"}
          docsLinkPath="/docs/components/concepts/global-execution-listeners/"
          handleClick={addGlobalExecutionListener}
        />
        {addGlobalExecutionListenerModal}
      </Page>
    );
  }

  const transformedData = globalExecutionListeners?.items.map((listener) => {
    const isConfigSource = listener.source === "CONFIGURATION";
    return {
      ...listener,
      afterNonGlobal: listener.afterNonGlobal
        ? t("executionOrderAfter")
        : t("executionOrderBefore"),
      eventTypes: getEventTypeLabels(listener.eventTypes, t),
      source: (
        <Tag type={isConfigSource ? "cool-gray" : "blue"}>
          {isConfigSource ? t("sourceConfiguration") : t("sourceApi")}
        </Tag>
      ),
      originalListener: listener,
    };
  });

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={transformedData ?? []}
        headers={[
          {
            header: t("globalExecutionListenerId"),
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
          { header: t("source"), key: "source", isSortable: false },
        ]}
        addEntityLabel={t("createListener")}
        onAddEntity={addGlobalExecutionListener}
        loading={loading}
        menuItems={[
          {
            label: t("editGlobalExecutionListener"),
            icon: Edit,
            disabled: (entity) =>
              entity.originalListener.source === "CONFIGURATION",
            onClick: (entity) =>
              editGlobalExecutionListener(entity.originalListener),
          },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            disabled: (entity) =>
              entity.originalListener.source === "CONFIGURATION",
            onClick: (entity) =>
              deleteGlobalExecutionListener(entity.originalListener),
          },
        ]}
        searchPlaceholder={t("searchById")}
        searchKey="id"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("globalExecutionListenersCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addGlobalExecutionListenerModal}
      {editGlobalExecutionListenerModal}
      {deleteGlobalExecutionListenerModal}
    </Page>
  );
};

export default List;
