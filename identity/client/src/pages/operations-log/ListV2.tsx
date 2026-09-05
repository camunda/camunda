/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useEffect, useMemo, useState } from "react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import EntityList from "src/components/entityListV2";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import { usePagination, SortConfig } from "src/utility/api";
import { useQuery } from "@tanstack/react-query";
import { auditLogQueries } from "src/utility/api/audit-logs/queries";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize";
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  PageContentLayout,
  Text,
} from "@camunda/design-system";
import TextField from "src/components/formV2/TextField";
import useDebounce from "react-debounced";
import { useForm, FieldPath, FieldPathValue } from "react-hook-form";
import { CellProperty } from "src/pages/operations-log/CellPropertyV2";
import { CircleCheck, Plug, User, XCircle } from "lucide-react";
import AiAgentIcon from "src/assets/images/ai-agent.svg";
// TODO: Replace with `DateRangePicker` from design system
import { DateRangeField } from "src/components/form/DateRangeField";
import {
  ALLOWED_ENTITY_TYPES,
  ALLOWED_OPERATION_TYPES,
  ALLOWED_RESULT_TYPES,
  AuditLogFilters,
  auditLogSearchParamsSync,
} from "src/pages/operations-log/filters";
import { useSearchParamsFilters } from "src/utility/filters/useSearchParamsFilters";
import { Select } from "src/components/formV2/Select";

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

  const { watch, setValue, reset, subscribe } = useForm<AuditLogFilters>({
    values: searchParamsFilters,
  });
  const filters = watch();

  useEffect(
    () =>
      subscribe({
        formState: { values: true },
        callback: ({ values }) => setSearchParamsFilters(values),
      }),
    [subscribe, setSearchParamsFilters],
  );

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
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery(
    auditLogQueries.search({
      sort: transformedSort,
      filter: {
        category: {
          $eq: "ADMIN",
        },
        result: filters.result,
        operationType: filters.operationType,
        entityType: filters.entityType,
        relatedEntityType: filters.relatedEntityType,
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
    }),
  );

  const handleSort = useCallback(
    (sortConfig: SortConfig[] | undefined) => {
      setPaginationSort(sortConfig);
    },
    [setPaginationSort],
  );

  const onSelectValueChange = <K extends FieldPath<AuditLogFilters>>(
    field: K,
    value: FieldPathValue<AuditLogFilters, K> | undefined,
  ) => {
    setValue(field, value as FieldPathValue<AuditLogFilters, K>);
  };

  return (
    <Page>
      <PageHeader
        title={t("operationsLog")}
        linkText={t("operationsLog").toLowerCase()}
        docsLinkPath="/components/admin/audit-operations/"
      />
      <PageContentLayout
        sidebarPosition="left"
        sidebarWidth={240}
        sidebar={
          <Card className="sticky top-0">
            <CardHeader>
              <CardTitle>{t("filter")}</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <Select
                id="operationType"
                items={[...ALLOWED_OPERATION_TYPES]}
                titleText={t("operationType")}
                selectedItem={filters.operationType}
                onChange={({ selectedItem }) => {
                  onSelectValueChange("operationType", selectedItem);
                }}
              />
              <Select
                id="entityType"
                items={[...ALLOWED_ENTITY_TYPES]}
                titleText={t("entityType")}
                selectedItem={filters.entityType}
                onChange={({ selectedItem }) => {
                  onSelectValueChange("entityType", selectedItem);

                  if (selectedItem !== "AUTHORIZATION") {
                    setValue("relatedEntityType", undefined);
                    setValue("relatedEntityKey", "");
                    setDebouncedRelatedEntityKey("");
                  }
                }}
              />
              {filters.entityType === "AUTHORIZATION" && (
                <>
                  <Select
                    id="relatedEntityType"
                    items={[...ALLOWED_ENTITY_TYPES]}
                    titleText={t("ownerType")}
                    selectedItem={filters.relatedEntityType}
                    onChange={({ selectedItem }) => {
                      onSelectValueChange("relatedEntityType", selectedItem);
                    }}
                  />
                  <TextField
                    label={t("ownerKey")}
                    placeholder={t("ownerKeyPlaceholder")}
                    value={filters.relatedEntityKey}
                    onChange={(newValue) => {
                      const value = newValue.trim();
                      setValue("relatedEntityKey", value);
                      debounce(() => setDebouncedRelatedEntityKey(value ?? ""));
                    }}
                  />
                </>
              )}
              <Select
                titleText={t("status")}
                id="result-field"
                onChange={({ selectedItem }) => {
                  onSelectValueChange("result", selectedItem);
                }}
                items={[...ALLOWED_RESULT_TYPES]}
                selectedItem={filters.result}
              />
              <TextField
                label={t("actor")}
                placeholder={t("actorPlaceholder")}
                value={filters.actor}
                onChange={(newValue) => {
                  const value = newValue.trim();
                  setValue("actor", value);
                  debounce(() => setDebouncedActor(value ?? ""));
                }}
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
              <Button
                className="mx-auto w-fit"
                variant="ghost"
                size="sm"
                disabled={
                  !filters.operationType &&
                  !filters.entityType &&
                  !filters.result &&
                  !filters.actor &&
                  !filters.timestampFrom &&
                  !filters.timestampTo
                }
                type="reset"
                onClick={() => {
                  reset({
                    operationType: undefined,
                    entityType: undefined,
                    relatedEntityType: undefined,
                    relatedEntityKey: "",
                    result: undefined,
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
            </CardContent>
          </Card>
        }
      >
        <EntityList
          data={
            auditLogs?.items.map((log) => ({
              id: log.auditLogKey,
              result:
                log.result === "SUCCESS" ? (
                  <CircleCheck
                    role="img"
                    className="h-5 w-5 text-success-action-default"
                    aria-label={spaceAndCapitalize(log.result)}
                  />
                ) : (
                  <XCircle
                    role="img"
                    className="h-5 w-5 text-danger-action-default"
                    aria-label={spaceAndCapitalize(log.result)}
                  />
                ),
              operationType: spaceAndCapitalize(log.operationType),
              entityType: spaceAndCapitalize(log.entityType),
              reference: (
                <Text as="code" variant="code-sm">
                  {log.entityDescription?.trim() || log.entityKey}
                </Text>
              ),
              property: <CellProperty item={log} />,
              actorId: log.actorId ? (
                <div className="flex items-center gap-1">
                  {log.actorType === "CLIENT" ? (
                    <Plug role="img" className="h-4 w-4" aria-label="Client" />
                  ) : (
                    <User role="img" className="h-4 w-4" aria-label="User" />
                  )}
                  {log.agentElementId && <AiAgentIcon aria-label="Agent" />}
                  {log.actorId}
                </div>
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
      </PageContentLayout>
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("operationsLogCouldNotLoad")}
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
