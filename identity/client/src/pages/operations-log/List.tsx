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
  Heading,
  Stack,
  TextInput,
} from "@carbon/react";
import useDebounce from "react-debounced";
import { useForm, FieldPath, FieldPathValue } from "react-hook-form";
import { CellProperty } from "src/pages/operations-log/CellProperty";
import { Api, User } from "@carbon/react/icons";
import AiAgentIcon from "src/assets/images/ai-agent.svg";
import { DateRangeField } from "src/components/form/DateRangeField";
import {
  ALLOWED_ENTITY_TYPES,
  ALLOWED_OPERATION_TYPES,
  ALLOWED_RESULT_TYPES,
  AuditLogFilters,
  auditLogSearchParamsSync,
} from "src/pages/operations-log/filters";
import { useSearchParamsFilters } from "src/utility/filters/useSearchParamsFilters";
import { useUpdateEffect } from "src/utility/hooks/useUpdateEffect";
import { Select } from "src/components/form/Select";

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
  });

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
      <Grid condensed fullWidth>
        <ColumnRightPadding sm={4} md={3} lg={4} xlg={3}>
          <StickySection level={4}>
            <Stack gap={5}>
              <Heading>{t("filter")}</Heading>
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
              <Select
                titleText={t("status")}
                id="result-field"
                onChange={({ selectedItem }) => {
                  onSelectValueChange("result", selectedItem);
                }}
                items={[...ALLOWED_RESULT_TYPES]}
                selectedItem={filters.result}
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
                      {log.actorType === "CLIENT" ? (
                        <Api aria-label="Client" />
                      ) : (
                        <User aria-label="User" />
                      )}
                      {log.agentElementId && <AiAgentIcon aria-label="Agent" />}
                      {log.actorId}
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
