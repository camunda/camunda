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
  DatePickerWrapper,
} from "./components/styled";
import {
  Button,
  Column,
  DatePicker,
  DatePickerInput,
  Dropdown,
  FormLabel,
  Heading,
  MultiSelect,
  Section,
  Stack,
  TextInput,
} from "@carbon/react";
import {
  AuditLogEntityType,
  AuditLogOperationType,
  AuditLogResult,
  auditLogResultSchema,
} from "@camunda/camunda-api-zod-schemas/8.9";
import useDebounce from "react-debounced";
import { useForm } from "react-hook-form";
import { CellProperty } from "src/pages/operations-log/CellProperty";
import { User } from "@carbon/react/icons";

type AuditLogSort = { field: string; order: "asc" | "desc" };

type DateRange = {
  from?: string;
  to?: string;
};

type Filters = {
  operationType: AuditLogOperationType[];
  entityType: AuditLogEntityType[];
  result: AuditLogResult | "all";
  actor: string;
  timestampRange: DateRange;
};

const DEFAULT_SORT: AuditLogSort[] = [{ field: "timestamp", order: "desc" }];

const ORDER_MAP: Record<SortConfig["order"], AuditLogSort["order"]> = {
  ASC: "asc",
  DESC: "desc",
};

const ALLOWED_OPERATION_TYPES: AuditLogOperationType[] = [
  "CREATE",
  "ASSIGN",
  "UNASSIGN",
  "DELETE",
  "UPDATE",
] as const;

const ALLOWED_ENTITY_TYPES: AuditLogEntityType[] = [
  "AUTHORIZATION",
  "ROLE",
  "USER",
  "GROUP",
  "MAPPING_RULE",
  "TENANT",
] as const;

const List: FC = () => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate();

  const { watch, setValue, reset } = useForm<Filters>({
    defaultValues: {
      operationType: [],
      entityType: [],
      result: "all",
      actor: "",
      timestampRange: {},
    },
  });

  const operationType = watch("operationType");
  const entityType = watch("entityType");
  const result = watch("result");
  const actor = watch("actor");
  const timestampRange = watch("timestampRange");

  const debounce = useDebounce();
  const [debouncedActor, setDebouncedActor] = useState<string>();

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
      result: result !== "all" ? result : undefined,
      operationType:
        operationType && operationType.length > 0
          ? { $in: operationType }
          : undefined,
      entityType:
        entityType && entityType.length > 0 ? { $in: entityType } : undefined,
      actorId: debouncedActor,
      timestamp:
        timestampRange.from && timestampRange.to
          ? {
              $gte: timestampRange.from,
              $lte: timestampRange.to,
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
          <Section level={4}>
            <Stack gap={5}>
              <Heading>{t("filter")}</Heading>
              <MultiSelect
                id="operationType"
                items={ALLOWED_OPERATION_TYPES}
                titleText={t("operationType")}
                label={t("multiSelectLabel")}
                selectedItems={operationType ? operationType : []}
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
              <MultiSelect
                id="entityType"
                items={ALLOWED_ENTITY_TYPES}
                titleText={t("entityType")}
                label={t("multiSelectLabel")}
                selectedItems={entityType ? entityType : []}
                itemToString={(selectedItem) =>
                  spaceAndCapitalize(selectedItem)
                }
                onChange={({ selectedItems }) => {
                  if (selectedItems !== null) {
                    setValue("entityType", selectedItems);
                  }
                }}
                size="sm"
              />
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
                items={["all", ...auditLogResultSchema.options]}
                itemToString={(item) =>
                  item === "all"
                    ? t("selectAll")
                    : item
                      ? spaceAndCapitalize(item)
                      : ""
                }
                selectedItem={result}
                size="sm"
              />
              <TextInput
                id="actorId"
                labelText={t("actor")}
                placeholder={t("actorPlaceholder")}
                value={actor}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const value = e.target.value.trim();
                  setValue("actor", value);
                  debounce(() => setDebouncedActor(value ? value : undefined));
                }}
                size="sm"
              />
              <FormLabel>{t("date")}</FormLabel>
              <DatePickerWrapper>
                <DatePicker
                  datePickerType="range"
                  dateFormat="Y-m-d"
                  value={[timestampRange.from ?? "", timestampRange.to ?? ""]}
                  onChange={(dates) => {
                    const [from, to] = dates;
                    setValue("timestampRange", {
                      from: from ? from.toISOString() : undefined,
                      to: to ? to.toISOString() : undefined,
                    });
                  }}
                >
                  <DatePickerInput
                    id="date-picker1"
                    labelText="From"
                    placeholder="YYYY-MM-DD"
                    hideLabel
                    size="sm"
                    // @ts-expect-error - autoComplete is not in type definition but supported by underlying input
                    autoComplete="off"
                  />
                  <DatePickerInput
                    id="date-picker2"
                    labelText="To"
                    placeholder="YYYY-MM-DD"
                    hideLabel
                    size="sm"
                    // @ts-expect-error - autoComplete is not in type definition but supported by underlying input
                    autoComplete="off"
                  />
                </DatePicker>
              </DatePickerWrapper>
              <CenteredRow>
                <Button
                  kind="ghost"
                  size="sm"
                  disabled={
                    operationType.length === 0 &&
                    entityType.length === 0 &&
                    result === "all" &&
                    !actor &&
                    !timestampRange.from &&
                    !timestampRange.to
                  }
                  type="reset"
                  onClick={() => {
                    reset({
                      operationType: [],
                      entityType: [],
                      result: "all",
                      actor: "",
                      timestampRange: {},
                    });
                    setDebouncedActor(undefined);
                  }}
                >
                  {t("reset")}
                </Button>
              </CenteredRow>
            </Stack>
          </Section>
        </ColumnRightPadding>
        <Column sm={4} md={5} lg={12} xlg={13}>
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
                reference: log.entityKey,
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
              { header: t("entityType"), key: "entityType", isSortable: true },
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
