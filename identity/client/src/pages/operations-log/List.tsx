/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { FC, useCallback, useMemo, useState } from "react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi, usePagination, SortConfig } from "src/utility/api";
import { searchAuditLogs } from "src/utility/api/audit-logs";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize";
import {
  OperationLogName,
  SuccessIcon,
  ErrorIcon,
  Grid,
  ColumnRightPadding,
  CenteredRow,
  StickySection,
  EntityListStickyWrapper,
} from "./components/styled";
import {
  Button,
  CodeSnippet,
  Column,
  Dropdown,
  Heading,
  MultiSelect,
  Stack,
  TextInput,
} from "@carbon/react";
import {
  type AuditLogEntityType,
  auditLogResultSchema,
} from "@camunda/camunda-api-zod-schemas/8.9";
import useDebounce from "react-debounced";
import { useForm } from "react-hook-form";
import { CellProperty } from "src/pages/operations-log/CellProperty";
import { User } from "@carbon/react/icons";
import { DateRangeField } from "src/components/form/DateRangeField";
import {
  ALLOWED_ENTITY_TYPES,
  ALLOWED_OPERATION_TYPES,
  ALLOWED_RESULT_TYPES,
  AuditLogFilters,
  auditLogSearchParamsSync,
} from "src/pages/operations-log/filters";
import { useSearchParamsFilters } from "src/utility/filters/useSearchParamsFilters";
import { useUpdateEffect } from "src/utility/hooks/useUpdateEffect.ts";

type AuditLogSort = { field: string; order: "asc" | "desc" };

const DEFAULT_SORT: AuditLogSort[] = [{ field: "timestamp", order: "desc" }];

const ORDER_MAP: Record<SortConfig["order"], AuditLogSort["order"]> = {
  ASC: "asc",
  DESC: "desc",
};

const List: FC = () => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate();
  const { searchParamsFilters, setSearchParamsFilters } =
    useSearchParamsFilters(auditLogSearchParamsSync);

  const { watch, setValue, reset } = useForm<AuditLogFilters>({
    values: searchParamsFilters,
  });
  const filters = watch();

  useUpdateEffect(() => {
    setSearchParamsFilters(filters);
  }, [filters]);

  const debounce = useDebounce(500);
  const [debouncedActor, setDebouncedActor] = useState<string>(filters.actor);
  const [debouncedRelatedEntityKey, setDebouncedRelatedEntityKey] =
    useState<string>(filters.relatedEntityKey);

  const [isDateRangeModalOpen, setIsDateRangeModalOpen] =
    useState<boolean>(false);

  const {
    pageParams,
    page,
    setPageNumber,
    setPageSize,
    setSort: setPaginationSort,
  } = usePagination({
    pageNumber: 1,
    pageSize: 50,
  });

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
      result: filters.result !== "all" ? filters.result : undefined,
      operationType:
        filters.operationType && filters.operationType.length > 0
          ? { $in: filters.operationType }
          : undefined,
      entityType: filters.entityType ? { $eq: filters.entityType } : undefined,
      relatedEntityType: filters.relatedEntityType
        ? { $eq: filters.relatedEntityType }
        : undefined,
      relatedEntityKey: debouncedRelatedEntityKey
        ? debouncedRelatedEntityKey
        : undefined,
      actorId: debouncedActor ? debouncedActor : undefined,
      timestamp:
        filters.timestampFrom && filters.timestampTo
          ? {
              $gte: filters.timestampFrom,
              $lte: filters.timestampTo,
            }
          : undefined,
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
      <Grid condensed fullWidth>
        <ColumnRightPadding sm={4} md={3} lg={4} xlg={3}>
          <StickySection level={4}>
            <Stack gap={5}>
              <Heading>{t("filter")}</Heading>
              <MultiSelect
                id="operationType"
                items={[...ALLOWED_OPERATION_TYPES]}
                titleText={t("operationType")}
                label={t("multiSelectLabel")}
                selectedItems={filters.operationType}
                itemToString={(selectedItem) =>
                  spaceAndCapitalize(selectedItem)
                }
                onChange={({ selectedItems }) => {
                  if (selectedItems !== null) {
                    setValue("operationType", selectedItems);
                  }
                }}
                size="sm"
              />
              <Dropdown
                id="entityType"
                items={[...ALLOWED_ENTITY_TYPES]}
                titleText={t("entityType")}
                label={t("selectLabel")}
                selectedItem={filters.entityType ? filters.entityType : ""}
                itemToString={(selectedItem) =>
                  selectedItem ? spaceAndCapitalize(selectedItem) : ""
                }
                onChange={({
                  selectedItem,
                }: {
                  selectedItem: AuditLogEntityType;
                }) => {
                  setValue("entityType", selectedItem);

                  if (selectedItem !== "AUTHORIZATION") {
                    setValue("relatedEntityType", undefined);
                    setValue("relatedEntityKey", "");
                    setDebouncedRelatedEntityKey("");
                  }
                }}
                size="sm"
              />
              {filters.entityType === "AUTHORIZATION" && (
                <>
                  <Dropdown
                    id="relatedEntityType"
                    items={[...ALLOWED_ENTITY_TYPES]}
                    titleText={t("ownerType")}
                    label={t("selectLabel")}
                    selectedItem={
                      filters.relatedEntityType ? filters.relatedEntityType : ""
                    }
                    itemToString={(selectedItem) =>
                      selectedItem ? spaceAndCapitalize(selectedItem) : ""
                    }
                    onChange={({
                      selectedItem,
                    }: {
                      selectedItem: AuditLogEntityType;
                    }) => {
                      setValue("relatedEntityType", selectedItem);
                    }}
                  />
                  <TextInput
                    id="relatedEntityKey"
                    labelText={t("ownerKey")}
                    placeholder={t("ownerKeyPlaceholder")}
                    value={filters.relatedEntityKey}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                      const value = e.target.value.trim();
                      setValue("relatedEntityKey", value);
                      debounce(() => setDebouncedRelatedEntityKey(value ?? ""));
                    }}
                    size="sm"
                  />
                </>
              )}
              <Dropdown
                label={t("selectLabel")}
                aria-label={t("selectLabel")}
                titleText={t("status")}
                id="result-field"
                onChange={({ selectedItem }) => {
                  setValue(
                    "result",
                    !selectedItem || selectedItem === "all"
                      ? "all"
                      : auditLogResultSchema.parse(selectedItem),
                  );
                }}
                items={[...ALLOWED_RESULT_TYPES]}
                itemToString={(item) =>
                  item === "all"
                    ? t("selectAll")
                    : item
                      ? spaceAndCapitalize(item)
                      : ""
                }
                selectedItem={filters.result}
                size="sm"
              />
              <TextInput
                id="actorId"
                labelText={t("actor")}
                placeholder={t("actorPlaceholder")}
                value={filters.actor}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const value = e.target.value.trim();
                  setValue("actor", value);
                  debounce(() => setDebouncedActor(value ?? ""));
                }}
                size="sm"
              />
              <DateRangeField
                isModalOpen={isDateRangeModalOpen}
                onModalClose={() => setIsDateRangeModalOpen(false)}
                onClick={() => setIsDateRangeModalOpen(true)}
                value={{
                  from: filters.timestampFrom ?? "",
                  to: filters.timestampTo ?? "",
                }}
                onChange={([from, to]) => {
                  setValue("timestampFrom", from ? from.toISOString() : "");
                  setValue("timestampTo", to ? to.toISOString() : "");
                }}
                popoverTitle="Filter by timestamp date range"
                label={t("date")}
              />
              <CenteredRow>
                <Button
                  kind="ghost"
                  size="sm"
                  disabled={
                    filters.operationType.length === 0 &&
                    !filters.entityType &&
                    filters.result === "all" &&
                    !filters.actor &&
                    !filters.timestampFrom &&
                    !filters.timestampTo
                  }
                  type="reset"
                  onClick={() => {
                    reset({
                      operationType: [],
                      entityType: undefined,
                      relatedEntityType: undefined,
                      relatedEntityKey: "",
                      result: "all",
                      actor: "",
                      timestampFrom: "",
                      timestampTo: "",
                    });
                    setDebouncedActor("");
                    setDebouncedRelatedEntityKey("");
                  }}
                >
                  {t("reset")}
                </Button>
              </CenteredRow>
            </Stack>
          </StickySection>
        </ColumnRightPadding>
        <Column sm={4} md={5} lg={12} xlg={13}>
          <EntityListStickyWrapper>
            <EntityList
              data={
                auditLogs?.items.map((log) => ({
                  id: log.auditLogKey,
                  result:
                    log.result === "SUCCESS" ? (
                      <SuccessIcon size={20} />
                    ) : (
                      <ErrorIcon size={20} />
                    ),
                  operationType: (
                    <OperationLogName>
                      {spaceAndCapitalize(log.operationType)}
                    </OperationLogName>
                  ),
                  entityType: spaceAndCapitalize(log.entityType),
                  reference: (
                    <CodeSnippet type="inline">
                      {log.entityDescription?.trim() || log.entityKey}
                    </CodeSnippet>
                  ),
                  property: <CellProperty item={log} />,
                  actorId: log.actorId ? (
                    <OperationLogName>
                      <User /> {log.actorId}
                    </OperationLogName>
                  ) : (
                    "-"
                  ),
                  timestamp: new Date(log.timestamp).toLocaleString(),
                })) || []
              }
              headers={[
                { header: "", key: "result" },
                {
                  header: t("operationType"),
                  key: "operationType",
                  isSortable: true,
                },
                {
                  header: t("entityType"),
                  key: "entityType",
                  isSortable: true,
                },
                { header: t("reference"), key: "reference" },
                { header: t("property"), key: "property" },
                { header: t("actor"), key: "actorId", isSortable: true },
                { header: t("date"), key: "timestamp", isSortable: true },
              ]}
              loading={loading}
              setSort={handleSort}
              page={{ ...page, ...auditLogs?.page }}
              pageSizes={[50, 100, 200]}
              setPageNumber={setPageNumber}
              setPageSize={setPageSize}
            />
          </EntityListStickyWrapper>
        </Column>
      </Grid>
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
