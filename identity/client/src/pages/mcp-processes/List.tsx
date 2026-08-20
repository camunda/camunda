/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useMemo } from "react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList, {
  type DataTableHeader,
} from "src/components/entityList/EntityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useMcpProcessTools, type McpProcessTool } from "./useMcpProcessTools";
import { ExpandedToolDetails } from "./components/ExpandedToolDetails";

type ListProps = {
  isTenantsApiEnabled: boolean;
};

const List: FC<ListProps> = ({ isTenantsApiEnabled }) => {
  const { t } = useTranslate("mcpProcesses");
  const { t: tComponents } = useTranslate();

  const {
    processTools,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
    ...paginationProps
  } = useMcpProcessTools();

  const headers = useMemo<DataTableHeader<McpProcessTool>[]>(() => {
    const columns: DataTableHeader<McpProcessTool>[] = [
      { header: t("toolName"), key: "toolName", isSortable: true },
      { header: t("toolDescription"), key: "toolDescription" },
      { header: t("processName"), key: "processDefinitionName" },
      { header: t("version"), key: "processDefinitionVersion" },
    ];

    if (isTenantsApiEnabled) {
      columns.push({ header: t("tenant"), key: "tenantId" });
    }

    return columns;
  }, [isTenantsApiEnabled, t]);

  return (
    <Page>
      <PageHeader
        title={tComponents("mcpProcesses")}
        linkText={tComponents("mcpProcesses").toLowerCase()}
        docsLinkPath="/components/admin/mcp-processes/"
      />
      <EntityList
        data={processTools}
        headers={headers}
        loading={loading}
        searchKey="toolName"
        searchPlaceholder={t("searchByToolName")}
        renderExpandedRow={(entity) => <ExpandedToolDetails tool={entity} />}
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("mcpProcessesCouldNotLoad")}
          actionButton={{
            label: tComponents("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </Page>
  );
};

export default List;
