/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import {
  InlineNotification,
  TranslatedErrorInlineNotification,
} from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import { usePaginatedApi } from "src/utility/api";
import { searchClusterVariables } from "src/utility/api/cluster-variables";
import PageEmptyState from "src/components/layout/PageEmptyState";
import { AddModal } from "./modals/add-modal";
import DeleteModal from "./modals/DeleteModal";
import DetailsModal from "./modals/DetailsModal";
import EditModal from "./modals/EditModal";
import { usePollingReload } from "src/utility/hooks/usePollingReload";
import { type QueryClusterVariablesResponseBody } from "@camunda/camunda-api-zod-schemas/8.10";
import { useCallback } from "react";

export default function List() {
  const { t } = useTranslate("clusterVariables");
  const {
    data: clusterVariables,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchClusterVariables);

  const compareClusterVariables = useCallback(
    (current: QueryClusterVariablesResponseBody) => {
      const previous = clusterVariables?.items ?? [];
      if (previous.length !== current.items.length) {
        return true;
      }

      return previous.some((prev) => {
        const match = current.items.find(
          (curr) =>
            curr.name === prev.name &&
            curr.scope === prev.scope &&
            curr.tenantId === prev.tenantId,
        );
        if (!match) {
          return true;
        }
        return JSON.stringify(prev.value) !== JSON.stringify(match.value);
      });
    },
    [clusterVariables],
  );

  const { startPolling, pollingStatus, resetPollingStatus, isPolling } =
    usePollingReload<QueryClusterVariablesResponseBody>(
      reload,
      compareClusterVariables,
    );

  const isInitialLoad = loading && !isPolling;

  const reloadWithPolling = () => {
    startPolling();
  };

  const [addClusterVariable, addClusterVariableModal] = useModal(
    AddModal,
    reloadWithPolling,
  );
  const [deleteClusterVariable, deleteClusterVariableModal] = useEntityModal(
    DeleteModal,
    reloadWithPolling,
  );
  const [viewClusterVariable, viewClusterVariableModal] = useEntityModal(
    DetailsModal,
    () => {},
  );
  const [editClusterVariable, editClusterVariableModal] = useEntityModal(
    EditModal,
    reloadWithPolling,
  );

  const shouldShowEmptyState =
    success && !search && !clusterVariables?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("clusterVariables")}
      linkText={t("clusterVariables").toLowerCase()}
      docsLinkPath="/components/modeler/feel/cluster-variable/cluster-variable-overview/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"clusterVariable"}
          docsLinkPath="/components/modeler/feel/cluster-variable/cluster-variable-overview/"
          handleClick={addClusterVariable}
        />
        {addClusterVariableModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={
          clusterVariables?.items.map((clusterVar) => {
            return {
              ...clusterVar,
              value: clusterVar.value,
              scopeValue:
                clusterVar.scope === "GLOBAL"
                  ? t("clusterVariableScopeTypeGlobal")
                  : `${t("clusterVariableScopeTypeTenant")}: ${clusterVar.tenantId}`,
            };
          }) || []
        }
        headers={[
          { header: t("name"), key: "name", isSortable: true },
          { header: t("value"), key: "value" },
          { header: t("scope"), key: "scopeValue" },
        ]}
        addEntityLabel={t("createClusterVariable")}
        onAddEntity={addClusterVariable}
        loading={isInitialLoad}
        menuItems={[
          {
            label: t("view"),
            onClick: ({ name, value }) => {
              viewClusterVariable({
                name,
                value,
              });
            },
          },
          {
            label: t("edit"),
            onClick: ({ name, value, scope, tenantId }) => {
              editClusterVariable({
                name,
                value,
                scope,
                tenantId,
              });
            },
          },
          {
            label: t("delete"),
            isDangerous: true,
            onClick: ({ name, scope, tenantId }) => {
              deleteClusterVariable({
                name,
                scope,
                tenantId,
              });
            },
          },
        ]}
        searchPlaceholder={t("searchByClusterVariableName")}
        searchKey="name"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("clusterVariablesCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {pollingStatus === "timeout" && (
        <InlineNotification
          kind="warning"
          title={t("clusterVariableUpdateTakingLonger")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              resetPollingStatus();
              void reload();
            },
          }}
        />
      )}
      {addClusterVariableModal}
      {deleteClusterVariableModal}
      {viewClusterVariableModal}
      {editClusterVariableModal}
    </Page>
  );
}
