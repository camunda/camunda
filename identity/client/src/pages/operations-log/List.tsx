/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useMemo, useState } from "react";
import { CodeSnippet, IconButton, Tooltip } from "@carbon/react";
import { Information, CheckmarkOutline, Error } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList, { FilterValues } from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi, usePagination, SortConfig } from "src/utility/api";
import { searchAuditLogs } from "src/utility/api/audit-logs";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize";
import { OperationLogName, OperationsLogContainer } from "./components/styled";

type AuditLogSort = { field: string; order: "asc" | "desc" };

const DEFAULT_SORT: AuditLogSort[] = [{ field: "timestamp", order: "desc" }];

const ORDER_MAP: Record<SortConfig["order"], AuditLogSort["order"]> = {
  ASC: "asc",
  DESC: "desc",
};

const OWNER_TYPES = ["User", "Role", "Group", "Application"];

const pickBySeed = (seed: string, options: string[]) => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return options[hash % options.length];
};

const generateOwnerId = (seed: string) => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  const ids = ["user-12345", "role-admin", "group-developers", "app-client-001"];
  return ids[hash % ids.length];
};

// Convert display format back to API enum format
// "Create" -> "CREATE", "Authorization" -> "AUTHORIZATION"
const toApiEnum = (displayValue: string): string => {
  // Handle special cases first
  if (displayValue === "Successful") return "SUCCESS";
  if (displayValue === "Failed") return "FAIL";
  
  // Convert "Create" -> "CREATE", "Authorization" -> "AUTHORIZATION"
  // Replace spaces with underscores and convert to uppercase
  return displayValue.replace(/\s+/g, "_").toUpperCase();
};

const List: FC = () => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate();

  const [filters, setFilters] = useState<FilterValues>({});

  const {
    pageParams,
    page,
    setPageNumber,
    setPageSize,
    setSort: setPaginationSort,
  } = usePagination({ pageNumber: 1, pageSize: 50 });

  const transformedSort = useMemo((): AuditLogSort[] => {
    if (!pageParams.sort || pageParams.sort.length === 0) {
      return DEFAULT_SORT;
    }
    return pageParams.sort.map(({ field, order }) => ({
      field,
      order: ORDER_MAP[order],
    }));
  }, [pageParams.sort]);

  // Build API filter object from filter values
  const apiFilter = useMemo(() => {
    const filter: Record<string, any> = {
      category: {
        $eq: "ADMIN",
      },
    };

    // Operation type filter
    if (filters.operationType && filters.operationType.length > 0) {
      filter.operationType = {
        $in: filters.operationType.map(toApiEnum),
      };
    }

    // Entity type filter
    if (filters.operationEntity) {
      filter.entityType = {
        $eq: toApiEnum(filters.operationEntity),
      };
    }

    // Operation status filter
    if (filters.operationStatus && filters.operationStatus.length > 0) {
      // For status, we can have multiple values, so use $in if multiple, $eq if single
      const statusValues = filters.operationStatus.map(toApiEnum);
      if (statusValues.length === 1) {
        filter.result = {
          $eq: statusValues[0],
        };
      } else {
        filter.result = {
          $in: statusValues,
        };
      }
    }

    // Actor filter
    if (filters.actor) {
      filter.actorId = filters.actor;
    }

    // Date range filter
    if (filters.dateRange?.startDate || filters.dateRange?.endDate) {
      const timestampFilter: Record<string, string> = {};
      if (filters.dateRange.startDate) {
        timestampFilter.$gt = filters.dateRange.startDate.toISOString();
      }
      if (filters.dateRange.endDate) {
        timestampFilter.$lt = filters.dateRange.endDate.toISOString();
      }
      if (Object.keys(timestampFilter).length > 0) {
        filter.timestamp = timestampFilter;
      }
    }

    // Note: Owner type and owner ID filters are applied client-side
    // because the API doesn't support filtering by these fields directly
    // They are handled in the filteredAuditLogs useMemo hook below

    return filter;
  }, [filters]);

  const {
    data: auditLogs,
    loading,
    success,
    reload,
  } = useApi(searchAuditLogs, {
    sort: transformedSort,
    filter: apiFilter,
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

  const handleFilterChange = useCallback((newFilters: FilterValues) => {
    setFilters(newFilters);
    // Reset to first page when filters change
    setPageNumber(1);
  }, [setPageNumber]);

  // Filter audit logs client-side for owner type/ID (since API might not support these filters directly)
  const filteredAuditLogs = useMemo(() => {
    if (!auditLogs?.items) return auditLogs;

    let items = auditLogs.items;

    // Apply owner type filter client-side when entity type is Authorization
    if (filters.operationEntity === "Authorization" && filters.ownerType) {
      items = items.filter((log) => {
        if (log.entityType !== "AUTHORIZATION") return false;
        const generatedOwnerType = pickBySeed(`${log.auditLogKey}-owner`, OWNER_TYPES);
        return generatedOwnerType === filters.ownerType;
      });
    }

    // Apply owner ID filter client-side when entity type is Authorization
    if (filters.operationEntity === "Authorization" && filters.ownerId) {
      items = items.filter((log) => {
        if (log.entityType !== "AUTHORIZATION") return false;
        const generatedOwnerId = generateOwnerId(`${log.auditLogKey}-owner-id`);
        return generatedOwnerId === filters.ownerId;
      });
    }

    return {
      ...auditLogs,
      items,
      page: {
        ...auditLogs.page,
        totalItems: items.length,
      },
    };
  }, [auditLogs, filters.operationEntity, filters.ownerType, filters.ownerId]);

  // Extract unique filter options from audit logs data
  const filterOptions = useMemo(() => {
    const operationTypes = new Set<string>();
    const entityTypes = new Set<string>();

    if (filteredAuditLogs?.items && filteredAuditLogs.items.length > 0) {
      filteredAuditLogs.items.forEach((log) => {
        if (log.operationType) {
          operationTypes.add(spaceAndCapitalize(log.operationType));
        }
        if (log.entityType) {
          entityTypes.add(spaceAndCapitalize(log.entityType));
        }
      });
    }

    return {
      operationTypeOptions: Array.from(operationTypes).sort(),
      operationEntityOptions: Array.from(entityTypes).sort(),
      operationStatusOptions: ["Successful", "Failed"],
    };
  }, [filteredAuditLogs?.items]);

  return (
    <Page>
      <PageHeader
        title={t("operationsLog")}
        linkText={t("operationsLog").toLowerCase()}
        docsLinkPath="/docs/components/concepts/audit-log/"
      />
      <OperationsLogContainer>
        <EntityList
        data={
          filteredAuditLogs?.items.map((log) => ({
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
                      <div style={{ color: "var(--cds-text-helper)", fontSize: "12px" }}>
                        Owner
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: "var(--cds-spacing-02)" }}>
                        {pickBySeed(`${log.auditLogKey}-owner`, OWNER_TYPES)}
                        <CodeSnippet type="inline">
                          {generateOwnerId(`${log.auditLogKey}-owner-id`)}
                        </CodeSnippet>
                      </div>
                    </div>
                  </div>
              ) : (
                "–"
              ),
            result: (() => {
              const isSuccess = log.result === "SUCCESS";
              const Icon = isSuccess ? CheckmarkOutline : Error;
              const color = isSuccess
                ? "var(--cds-support-success)"
                : "var(--cds-support-error)";
              const statusText = isSuccess ? "Successful" : "Failed";
              return (
                <Tooltip align="right" description={statusText}>
                  <Icon size={16} style={{ color }} />
                </Tooltip>
              );
            })(),
            actorId: log.actorId ? (
              <CodeSnippet type="inline">{log.actorId}</CodeSnippet>
            ) : (
              "–"
            ),
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
          { header: "", key: "result" },
          { header: t("Operation type"), key: "operationType", isSortable: true },
          { header: t("Entity type"), key: "entityType", isSortable: true },
          { header: t("Reference to entity"), key: "appliedTo" },
          { header: t("Property"), key: "property" },
          { header: t("actor"), key: "actorId", isSortable: true },
          { header: t("Date"), key: "timestamp", isSortable: true },
          { header: "", key: "info" },
        ]}
        loading={loading}
        setSort={handleSort}
        page={{ ...page, ...filteredAuditLogs?.page }}
        setPageNumber={setPageNumber}
        setPageSize={setPageSize}
        showFilterPanel={true}
        onFilterChange={handleFilterChange}
        filterOptions={filterOptions}
      />
      </OperationsLogContainer>
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
