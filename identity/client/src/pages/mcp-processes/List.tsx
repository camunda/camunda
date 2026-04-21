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

  return (
    <Page>
      <PageHeader
        title={tComponents("mcpProcesses")}
        linkText={tComponents("mcpProcesses").toLowerCase()}
        shouldShowDocumentationLink={false}
      />
      <EntityList
        data={processTools}
        headers={headers}
        loading={loading}
        searchKey="toolName"
        searchPlaceholder={t("searchByToolName")}
        {...paginationProps}
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
