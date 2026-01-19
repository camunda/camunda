/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useMemo } from "react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi, usePagination, SortConfig } from "src/utility/api";
import { searchAuditLogs } from "src/utility/api/audit-logs";

type AuditLogSort = { field: string; order: "asc" | "desc" };

const DEFAULT_SORT: AuditLogSort[] = [{ field: "timestamp", order: "desc" }];

const ORDER_MAP: Record<SortConfig["order"], AuditLogSort["order"]> = {
  ASC: "asc",
  DESC: "desc",
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
  } = usePagination();

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
            operationType: log.operationType,
            entityType: log.entityType,
            result: log.result,
            actorId: log.actorId,
            timestamp: new Date(log.timestamp).toLocaleString(),
          })) || []
        }
        headers={[
          { header: t("operation"), key: "operationType", isSortable: true },
          { header: t("entity"), key: "entityType", isSortable: true },
          { header: t("status"), key: "result" },
          { header: t("actor"), key: "actorId", isSortable: true },
          { header: t("time"), key: "timestamp", isSortable: true },
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
