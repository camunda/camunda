/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useMemo } from "react";
import { CodeSnippet, IconButton } from "@carbon/react";
import { Information } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi, usePagination, SortConfig } from "src/utility/api";
import { searchAuditLogs } from "src/utility/api/audit-logs";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize";
import { OperationLogName, SuccessIcon, ErrorIcon } from "./components/styled";

type AuditLogSort = { field: string; order: "asc" | "desc" };

const DEFAULT_SORT: AuditLogSort[] = [{ field: "timestamp", order: "desc" }];

const ORDER_MAP: Record<SortConfig["order"], AuditLogSort["order"]> = {
  ASC: "asc",
  DESC: "desc",
};

const OWNER_TYPES = ["User", "Role", "Group", "Application"];
const RESOURCE_TYPES = ["Component", "Tenant", "User task", "Resource", "System", "Batch"];

const pickBySeed = (seed: string, options: string[]) => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return options[hash % options.length];
};

const List: FC = () => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate();

  const {
    pageParams,
    page,
    setPageNumber,
    setPageSize,
    setSort: setPaginationSort,
  } = usePagination({ pageNumber: 1, pageSize: 20 });

  const transformedSort = useMemo((): AuditLogSort[] => {
    if (!pageParams.sort || pageParams.sort.length === 0) {
      return DEFAULT_SORT;
    }
    return pageParams.sort.map(({ field, order }) => ({
      field,
      order: ORDER_MAP[order],
    }));
  }, [pageParams.sort]);

  const {
    data: auditLogs,
    loading,
    success,
    reload,
  } = useApi(searchAuditLogs, {
    sort: transformedSort,
    filter: {
      category: {
        $eq: "ADMIN",
      },
    },
    page: {
      from: pageParams.page.from,
      limit: pageParams.page.limit,
    },
  });

  const handleSort = useCallback(
    (sortConfig: SortConfig[] | undefined) => {
      setPaginationSort(sortConfig);
    },
    [setPaginationSort],
  );

  return (
    <Page>
      <PageHeader
        title={t("operationsLog")}
        linkText={t("operationsLog").toLowerCase()}
        docsLinkPath="/docs/components/concepts/audit-log/"
      />
      <EntityList
        data={
          auditLogs?.items.map((log) => ({
            id: log.auditLogKey,
            operationType: (
              <OperationLogName>
                {spaceAndCapitalize(log.operationType)}
              </OperationLogName>
            ),
            entityType: spaceAndCapitalize(log.entityType),
            appliedTo: (
              <CodeSnippet type="inline">{log.entityKey}</CodeSnippet>
            ),
            property:
              log.entityType === "AUTHORIZATION" ? (
                <div>
                  <div>
                    <span style={{ color: "var(--cds-text-helper)" }}>
                      Owner type:
                    </span>{" "}
                    {pickBySeed(`${log.auditLogKey}-owner`, OWNER_TYPES)}
                  </div>
                  <div>
                    <span style={{ color: "var(--cds-text-helper)" }}>
                      Resource type:
                    </span>{" "}
                    {pickBySeed(`${log.auditLogKey}-resource`, RESOURCE_TYPES)}
                  </div>
                </div>
              ) : (
                "–"
              ),
            result: (
              <OperationLogName>
                {log.result === "SUCCESS" ? (
                  <SuccessIcon size={20} />
                ) : (
                  <ErrorIcon size={20} />
                )}
                {spaceAndCapitalize(log.result)}
              </OperationLogName>
            ),
            actorId: log.actorId,
            timestamp: new Date(log.timestamp).toLocaleString(),
            info: (
              <IconButton
                kind="ghost"
                size="sm"
                label="View details"
              >
                <Information size={16} />
              </IconButton>
            ),
          })) || []
        }
        headers={[
          { header: t("Operation type"), key: "operationType", isSortable: true },
          { header: t("entity"), key: "entityType", isSortable: true },
          { header: t("Reference"), key: "appliedTo" },
          { header: t("Properties"), key: "property" },
          { header: t("status"), key: "result", isSortable: true },
          { header: t("actor"), key: "actorId", isSortable: true },
          { header: t("time"), key: "timestamp", isSortable: true },
          { header: "", key: "info" },
        ]}
        loading={loading}
        setSort={handleSort}
        page={{ ...page, ...auditLogs?.page }}
        setPageNumber={setPageNumber}
        setPageSize={setPageSize}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("operationsLogCouldNotLoad")}
          actionButton={{ label: tComponents("retry"), onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
