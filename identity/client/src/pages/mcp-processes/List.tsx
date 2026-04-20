/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useMemo, useState } from "react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList, {
  type DataTableHeader,
} from "src/components/entityList/EntityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { isTenantsApiEnabled } from "src/configuration";
import { useMcpProcessTools, type McpProcessTool } from "./useMcpProcessTools";

const List: FC = () => {
  const { t } = useTranslate("mcpProcesses");
  const { t: tComponents } = useTranslate();

  const { processTools, loading, success, reload, ...paginationProps } =
    useMcpProcessTools();

  const headers = useMemo<DataTableHeader<McpProcessTool>[]>(() => {
    const columns: DataTableHeader<McpProcessTool>[] = [
      { header: t("toolName"), key: "toolName" },
      { header: t("toolDescription"), key: "toolDescription" },
      { header: t("processName"), key: "processDefinitionName" },
      { header: t("version"), key: "processDefinitionVersion" },
    ];

    if (isTenantsApiEnabled) {
      columns.push({ header: t("tenant"), key: "tenantId" });
    }

    return columns;
  }, [t]);

  // Note: Filtering by tool name is currently not supported by the API.
  const [toolNameSearch, setToolNameSearch] = useState("");
  const handleSearch = useCallback(
    (value: Record<string, string> | undefined) => {
      const toolName = value?.toolName.trim().toLowerCase();
      setToolNameSearch(toolName ?? "");
    },
    [],
  );

  const filteredTools = useMemo(() => {
    if (!toolNameSearch) return processTools;
    return processTools.filter((tool) =>
      tool.toolName.toLowerCase().includes(toolNameSearch),
    );
  }, [processTools, toolNameSearch]);

  return (
    <Page>
      <PageHeader
        title={tComponents("mcpProcesses")}
        linkText={tComponents("mcpProcesses").toLowerCase()}
        shouldShowDocumentationLink={false}
      />
      <EntityList
        data={filteredTools}
        headers={headers}
        loading={loading}
        searchKey="toolName"
        searchPlaceholder={t("searchByToolName")}
        {...paginationProps}
        setSearch={handleSearch}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("mcpProcessesCouldNotLoad")}
          actionButton={{ label: tComponents("retry"), onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
