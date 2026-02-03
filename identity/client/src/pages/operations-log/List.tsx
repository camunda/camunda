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
  Title,
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
  MultiSelect,
  Stack,
  TextInput,
} from "@carbon/react";
import {
  AuditLogEntityType,
  auditLogEntityTypeSchema,
  AuditLogOperationType,
  auditLogOperationTypeSchema,
  AuditLogResult,
  auditLogResultSchema,
} from "@camunda/camunda-api-zod-schemas/8.9";
import useDebounce from "react-debounced";

type AuditLogSort = { field: string; order: "asc" | "desc" };

type DateRange = {
  from?: string;
  to?: string;
};

const DEFAULT_SORT: AuditLogSort[] = [{ field: "timestamp", order: "desc" }];

const ORDER_MAP: Record<SortConfig["order"], AuditLogSort["order"]> = {
  ASC: "asc",
  DESC: "desc",
};

const List: FC = () => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate();

  const [operationType, setOperationType] = useState<AuditLogOperationType[]>(
    [],
  );
  const [entityType, setEntityType] = useState<AuditLogEntityType[]>([]);
  const [result, setResult] = useState<AuditLogResult | "all">("all");
  const debounce = useDebounce();
  const [actor, setActor] = useState<string>("");
  const [debouncedActor, setDebouncedActor] = useState<string>();
  const [timestampRange, setTimestampRange] = useState<DateRange>({});

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
          <Title>{t("operation")}</Title>
          <Stack gap={5}>
            <MultiSelect
              id="operationType"
              items={auditLogOperationTypeSchema.options}
              titleText={t("operationType")}
              label={t("multiSelectLabel")}
              selectedItems={operationType ? operationType : []}
              itemToString={(selectedItem) => spaceAndCapitalize(selectedItem)}
              onChange={({ selectedItems }) => {
                if (selectedItems !== null) {
                  setOperationType(selectedItems);
                }
              }}
              size="sm"
            />
            <MultiSelect
              id="entityType"
              items={auditLogEntityTypeSchema.options}
              titleText={t("entityType")}
              label={t("multiSelectLabel")}
              selectedItems={entityType ? entityType : []}
              itemToString={(selectedItem) => spaceAndCapitalize(selectedItem)}
              onChange={({ selectedItems }) => {
                if (selectedItems !== null) {
                  setEntityType(selectedItems);
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
                setResult(
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
                setActor(value);
                debounce(() => setDebouncedActor(value ? value : undefined));
              }}
              size="sm"
            />
            <FormLabel>{t("time")}</FormLabel>
            <DatePickerWrapper>
              <DatePicker
                datePickerType="range"
                dateFormat="Y-m-d"
                value={[timestampRange.from ?? "", timestampRange.to ?? ""]}
                onChange={(dates) => {
                  const [from, to] = dates;
                  setTimestampRange({
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
                  setOperationType([]);
                  setEntityType([]);
                  setResult("all");
                  setActor("");
                  setDebouncedActor(undefined);
                  setTimestampRange({});
                }}
              >
                {t("reset")}
              </Button>
            </CenteredRow>
          </Stack>
        </ColumnRightPadding>
        <Column sm={4} md={5} lg={12} xlg={13}>
          <EntityList
            data={
              auditLogs?.items.map((log) => ({
                id: log.auditLogKey,
                operationType: (
                  <OperationLogName>
                    {spaceAndCapitalize(log.operationType)}{" "}
                    {spaceAndCapitalize(log.entityType)}
                  </OperationLogName>
                ),
                entityType: spaceAndCapitalize(log.entityType),
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
                appliedTo: log.entityKey,
                actorId: log.actorId,
                timestamp: new Date(log.timestamp).toLocaleString(),
              })) || []
            }
            headers={[
              {
                header: t("operation"),
                key: "operationType",
                isSortable: true,
              },
              { header: t("entity"), key: "entityType", isSortable: true },
              { header: t("status"), key: "result" },
              { header: t("appliedTo"), key: "appliedTo" },
              { header: t("actor"), key: "actorId", isSortable: true },
              { header: t("time"), key: "timestamp", isSortable: true },
            ]}
            loading={loading}
            setSort={handleSort}
            page={{ ...page, ...auditLogs?.page }}
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
