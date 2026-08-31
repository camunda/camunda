/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode, useCallback, useMemo, useState } from "react";
import {
  Button,
  DataTable,
  type DataTableColumn,
  type DataTableRowAction,
  type PaginationConfig,
  type RowSelectionConfig,
  type SortingConfig,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@camunda/design-system";
import { LucideIcon, Plus } from "lucide-react";
import { DocumentationLink } from "src/components/documentationV2";
import useTranslate from "src/utility/localization";
import { PageResult, SortConfig } from "src/utility/api";
import SearchBar from "./SearchBar";

export type EntityData = {
  [key: string]: string | object | boolean | number | null;
} & {
  /** Stable identity for the row, required for selection and expansion. */
  id: string;
};

type HeaderData<D extends EntityData> = {
  [K in keyof D as D[K] extends string | ReactNode ? K : never]: D[K];
};

export type DataTableHeader<D extends EntityData> = {
  header: string;
  key: Extract<keyof HeaderData<D>, string | ReactNode>;
  isSortable?: boolean;
};

export type EntityListHeader<D extends EntityData> = DataTableHeader<D> & {
  key: Extract<keyof HeaderData<D>, string>;
};

type TextMenuItem<D> = {
  label: string;
  onClick: (entity: D) => void;
  isDangerous?: boolean;
  disabled?: boolean | ((entity: D) => boolean);
  hidden?: boolean;
};

type MenuItem<D> = TextMenuItem<D> & {
  icon?: LucideIcon;
};

type EntityListProps<D extends EntityData> = {
  description?: ReactNode | string;
  documentationPath?: string;
  searchPlaceholder?: string;
  searchKey?: string;
  data: D[] | null | undefined;
  headers: DataTableHeader<D>[];
  addEntityLabel?: string | null;
  addEntityDisabled?: boolean;
  onAddEntity?: () => void;
  onEntityClick?: (element: D) => void;
  menuItems?:
    | [MenuItem<D>]
    | [MenuItem<D>, MenuItem<D>]
    | [TextMenuItem<D>, TextMenuItem<D>, TextMenuItem<D>];
  loading?: boolean;
  isInsideModal?: boolean;
  title?: ReactNode;
  batchSelection?: {
    onSelect: (selected: D) => unknown;
    onUnselect: (selected: D) => unknown;
    onSelectAll: (selected: D[]) => unknown;
    isSelected: (selected: D) => boolean;
  };
  maxDisplayCellLength?: number;
  setPageNumber?: (page: number) => void;
  setPageSize?: (pageSize: number) => void;
  pageSizes?: number[];
  page?:
    | ({ pageNumber: number; pageSize: number } & Partial<PageResult>)
    | undefined;
  setSort?: (sort: SortConfig[] | undefined) => void;
  setSearch?: (search: Record<string, string> | undefined) => void;
  renderExpandedRow?: (entity: D) => ReactNode;
};

type SortingState = NonNullable<SortingConfig["sortState"]>;
type RowSelectionState = NonNullable<RowSelectionConfig["selectedRowIds"]>;

const INLINE_ACTIONS_THRESHOLD = 3;
const PAGESIZES = [10, 20, 30, 40, 50];

/**
 * Entities are handed to the table as-is, so every consumer callback gets the
 * original object back — object identity is part of the `batchSelection`
 * contract.
 */
const getRowId = (row: EntityData) => row.id;

const EntityList = <D extends EntityData>({
  title,
  isInsideModal = false,
  description,
  documentationPath,
  headers,
  data,
  addEntityLabel,
  addEntityDisabled,
  onAddEntity,
  onEntityClick,
  menuItems,
  loading,
  batchSelection,
  searchPlaceholder,
  searchKey,
  maxDisplayCellLength = 50,
  setPageNumber = () => {},
  setPageSize = () => {},
  pageSizes = PAGESIZES,
  page: pageData,
  setSort = () => {},
  setSearch = () => {},
  renderExpandedRow,
}: EntityListProps<D>): ReturnType<FC> => {
  const { t } = useTranslate("components");

  const [sortingState, setSortingState] = useState<SortingState>([]);

  const rows = useMemo(() => data ?? [], [data]);

  const isEntityClickable = onEntityClick !== undefined;

  const columns = useMemo<DataTableColumn<D>[]>(
    () =>
      headers.map((header, columnIndex) => {
        const key = String(header.key);

        return {
          id: key,
          // Load-bearing: TanStack's `getCanSort()` requires an accessor, so
          // without this no sort button is rendered for sortable headers.
          accessorFn: (row: D) => readCell(row, key),
          header: header.header,
          enableSorting: !!header.isSortable,
          // TanStack starts the cycle descending for columns whose first value
          // happens to be numeric; every column here should start ascending.
          sortDescFirst: false,
          cell: ({ row }) => {
            const value = readCell(row.original, key);

            // The first column is the primary cell: with `onRowClick` set the
            // DataTable wraps its content in a focusable `<button>`, which must
            // not contain the tooltip's own trigger button.
            return columnIndex === 0 && isEntityClickable
              ? value
              : truncateCell(value, maxDisplayCellLength);
          },
        };
      }),
    [headers, isEntityClickable, maxDisplayCellLength],
  );

  const rowActions = useMemo<DataTableRowAction<D>[] | undefined>(
    () =>
      menuItems?.map((menuItem) => {
        const { label, onClick, isDangerous, disabled, hidden } = menuItem;
        const Icon = "icon" in menuItem ? menuItem.icon : undefined;

        return {
          id: label,
          label,
          variant: isDangerous ? "destructive" : "default",
          icon: Icon ? <Icon aria-hidden="true" /> : undefined,
          iconOnly: !!Icon && !isDangerous,
          disabled: (row: D) => resolveMenuItemFlag(disabled, row),
          visible: (row: D) => !resolveMenuItemFlag(hidden, row),
          onClick,
        };
      }),
    [menuItems],
  );

  const selectedRowIds = useMemo<RowSelectionState>(() => {
    if (!batchSelection) {
      return {};
    }

    const selection: RowSelectionState = {};
    for (const row of rows) {
      if (batchSelection?.isSelected(row)) {
        selection[getRowId(row)] = true;
      }
    }
    return selection;
  }, [batchSelection, rows]);

  /**
   * The DataTable reports the whole next selection map, while `batchSelection`
   * expects a per-row / select-all split: a row checkbox flips exactly one row,
   * the header checkbox flips every remaining one.
   */
  const handleSelectedRowsChange = useCallback(
    (nextSelection: RowSelectionState) => {
      if (!batchSelection) {
        return;
      }

      const isNowSelected = (row: D) => !!nextSelection[getRowId(row)];
      const flipped = rows.filter(
        (row) => isNowSelected(row) !== batchSelection.isSelected(row),
      );

      if (flipped.length > 1) {
        batchSelection.onSelectAll(rows.filter(isNowSelected));
      } else if (flipped.length === 1) {
        const [row] = flipped;

        if (batchSelection.isSelected(row)) {
          batchSelection.onUnselect(row);
        } else {
          batchSelection.onSelect(row);
        }
      }
    },
    [batchSelection, rows],
  );

  const handleRowClick = (row: D) => {
    const textSelection = window.getSelection();

    if (
      onEntityClick &&
      (!textSelection || textSelection.toString().length === 0)
    ) {
      onEntityClick(row);
    }
  };

  // Kept explicit: a total that fits on the smallest page size has nothing to
  // page through, and the DS footer would still render a "Page 1 of 1" row.
  const pagination: PaginationConfig | undefined =
    pageData?.totalItems && pageData.totalItems > Math.min(...pageSizes)
      ? {
          manual: true,
          pageSizes,
          pageIndex: pageData.pageNumber - 1,
          pageSize: pageData.pageSize,
          rowCount: pageData.totalItems,
          onPaginationChange: ({ pageIndex, pageSize }) => {
            setPageNumber(pageIndex + 1);
            setPageSize(pageSize);
          },
        }
      : undefined;

  const descriptionNode =
    !description && !documentationPath ? undefined : (
      <>
        {description && <p>{description}</p>}
        {documentationPath && (
          <DocumentationLink path={documentationPath}>
            {t("Learn more")}
          </DocumentationLink>
        )}
      </>
    );

  return (
    <div className="flex flex-col gap-3">
      {(searchKey || addEntityLabel) && (
        <div className="flex items-center gap-3 justify-end">
          {searchKey && (
            <SearchBar
              searchKey={searchKey}
              searchPlaceholder={searchPlaceholder}
              onSearch={setSearch}
            />
          )}
          {addEntityLabel && (
            <Button onClick={onAddEntity} disabled={addEntityDisabled}>
              <Plus data-icon="inline-start" aria-hidden="true" />
              {addEntityLabel}
            </Button>
          )}
        </div>
      )}
      <DataTable<D>
        columns={columns}
        data={rows}
        getRowId={getRowId}
        title={isInsideModal ? undefined : title}
        description={isInsideModal ? undefined : descriptionNode}
        loading={loading}
        emptyState={t("No results found")}
        sorting={{
          manual: true,
          sortState: sortingState,
          onSortingChange: (state) => {
            setSortingState(state);
            setSort(toSortConfig(state));
          },
        }}
        pagination={pagination}
        rowActions={rowActions}
        inlineActionsThreshold={INLINE_ACTIONS_THRESHOLD}
        expansion={renderExpandedRow}
        onRowClick={isEntityClickable ? handleRowClick : undefined}
        rowSelection={
          batchSelection
            ? { selectedRowIds, onSelectedRowsChange: handleSelectedRowsChange }
            : undefined
        }
      />
    </div>
  );
};

/**
 * `DataTableHeader.key` constrains columns to values that are renderable
 * (`string | ReactNode`); arrays are joined into a single line.
 */
function readCell(row: EntityData, key: string): ReactNode {
  const value = row[key];

  if (Array.isArray(value)) {
    return value.join(", ");
  }

  if (value !== null && typeof value === "object") {
    return value as ReactNode;
  }

  return value;
}

function truncateCell(value: ReactNode, maxLength: number): ReactNode {
  if (typeof value !== "string" && typeof value !== "number") {
    return value;
  }

  const text = String(value);

  if (text.length <= maxLength) {
    return value;
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={text}
          className="[all:unset]"
        >{`${text.substring(0, maxLength)}…`}</button>
      </TooltipTrigger>
      {/* DS ships no width cap on tooltip content; long text would else stretch to the viewport edge. */}
      <TooltipContent className="max-w-md">{text}</TooltipContent>
    </Tooltip>
  );
}

function toSortConfig(state: SortingState): SortConfig[] | undefined {
  if (state.length === 0) {
    return undefined;
  }

  return state.map(({ id, desc }) => ({
    field: id,
    order: desc ? "DESC" : "ASC",
  }));
}

function resolveMenuItemFlag<D>(
  flag: boolean | ((entity: D) => boolean) | undefined,
  entity: D,
): boolean {
  return typeof flag === "function" ? flag(entity) : !!flag;
}

export default EntityList;
