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
import { Add, CarbonIconType } from "@carbon/react/icons";
import { DocumentationLink } from "src/components/documentationV2";
import Flex from "src/components/layoutV2/Flex";
import useTranslate from "src/utility/localization";
import { PageResult, SortConfig } from "src/utility/api";
import SearchBar from "./SearchBar";

export type EntityData = {
  [key: string]: string | object | boolean | number | null;
} & {
  id?: string;
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
  icon?: CarbonIconType;
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

/** Every row carries a stable id so selection and expansion survive re-renders. */
type EntityRow<D extends EntityData> = D & { id: string };

type SortingState = NonNullable<SortingConfig["sortState"]>;
type RowSelectionState = NonNullable<RowSelectionConfig["selectedRowIds"]>;

const MAX_ICON_ACTIONS = 2;
const PAGESIZES = [10, 20, 30, 40, 50];

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

  // `EntityList` never receives the *current* sort as a prop — only the
  // `setSort` setter — so the sort indicator is owned here, exactly as Carbon's
  // `DataTable` owned it internally. Rows are never reordered locally; the
  // server returns them already sorted.
  const [sortingState, setSortingState] = useState<SortingState>([]);

  const [entityIndex, tableData] = useMemo(() => {
    const nextIndex: { [id: string]: D } = {};
    const nextRows: EntityRow<D>[] = [];

    data?.forEach((dataset) => {
      const id = dataset.id || (Date.now() + Math.random()).toString();
      nextIndex[id] = dataset;
      nextRows.push({ ...dataset, id });
    });

    return [nextIndex, nextRows];
  }, [data]);

  // Rows are shallow copies, so every consumer callback is handed the original
  // object back — object identity is part of the `batchSelection` contract.
  const entityOf = useCallback(
    (row: EntityRow<D>) => entityIndex[row.id],
    [entityIndex],
  );

  const isEntityClickable = onEntityClick !== undefined;

  const columns = useMemo<DataTableColumn<EntityRow<D>>[]>(
    () =>
      headers.map((header, columnIndex) => {
        const key = String(header.key);

        return {
          id: key,
          // Load-bearing: TanStack's `getCanSort()` requires an accessor, so
          // without this no sort button is rendered for sortable headers.
          accessorFn: (row: EntityRow<D>) => readCell(row, key),
          header: header.header,
          enableSorting: !!header.isSortable,
          // Carbon always started the cycle ascending; TanStack would flip that
          // for columns whose first value happens to be numeric.
          sortDescFirst: false,
          cell: ({ row }) => {
            const value = readCell(row.original, key);

            // The first column is the primary cell: with `onRowClick` set the
            // DataTable wraps its content in a focusable `<button>`, which must
            // not contain the tooltip's own trigger button. Carbon left this
            // cell untruncated for the same reason (it rendered a `Link`).
            return columnIndex === 0 && isEntityClickable
              ? value
              : truncateCell(value, maxDisplayCellLength);
          },
        };
      }),
    [headers, isEntityClickable, maxDisplayCellLength],
  );

  const rowActions = useMemo<
    DataTableRowAction<EntityRow<D>>[] | undefined
  >(() => {
    // The prop type is a 1-to-3 tuple, so a non-nullish `menuItems` is never
    // empty — `undefined` is the only "no actions" case to guard against.
    if (!menuItems) {
      return undefined;
    }

    return menuItems.map((menuItem) => {
      const { label, onClick, isDangerous, disabled, hidden } = menuItem;
      const Icon = (menuItem as MenuItem<D>).icon;

      return {
        id: label,
        label,
        variant: isDangerous ? "destructive" : "default",
        icon: Icon ? () => <Icon aria-hidden="true" /> : undefined,
        disabled: (row) => resolveMenuItemFlag(disabled, entityOf(row)),
        visible: (row) => !resolveMenuItemFlag(hidden, entityOf(row)),
        onClick: (row) => onClick(entityOf(row)),
      };
    });
  }, [menuItems, entityOf]);

  const selectedRowIds = useMemo<RowSelectionState>(() => {
    if (!batchSelection) {
      return {};
    }

    const selection: RowSelectionState = {};

    tableData.forEach((row) => {
      if (batchSelection.isSelected(entityIndex[row.id])) {
        selection[row.id] = true;
      }
    });

    return selection;
  }, [batchSelection, entityIndex, tableData]);

  /**
   * TanStack reports the whole next selection map, while `batchSelection`
   * expects Carbon's per-row / select-all split. A single row checkbox flips
   * exactly one id; the header checkbox flips every remaining one, so a change
   * spanning more than one row is treated as "select all".
   */
  const handleSelectedRowsChange = useCallback(
    (nextSelection: RowSelectionState) => {
      if (!batchSelection) {
        return;
      }

      const flipped = tableData.filter(
        (row) => !!nextSelection[row.id] !== !!selectedRowIds[row.id],
      );

      if (flipped.length === 0) {
        return;
      }

      if (flipped.length > 1) {
        batchSelection.onSelectAll(
          tableData.filter((row) => !!nextSelection[row.id]).map(entityOf),
        );
        return;
      }

      const row = flipped[0];

      if (nextSelection[row.id]) {
        batchSelection.onSelect(entityOf(row));
      } else {
        batchSelection.onUnselect(entityOf(row));
      }
    },
    [batchSelection, entityOf, selectedRowIds, tableData],
  );

  const renderExpansion = renderExpandedRow;
  const expansion = useMemo(
    () =>
      renderExpansion
        ? (row: EntityRow<D>) => renderExpansion(entityOf(row))
        : undefined,
    [renderExpansion, entityOf],
  );

  const handleRowClick = (row: EntityRow<D>) => {
    const textSelection = window.getSelection();

    if (
      onEntityClick &&
      (!textSelection || textSelection.toString().length === 0)
    ) {
      onEntityClick(entityOf(row));
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
    <Flex direction="column" align="normal">
      {(searchKey || addEntityLabel) && (
        <Flex>
          {searchKey && (
            <SearchBar
              searchKey={searchKey}
              searchPlaceholder={searchPlaceholder}
              onSearch={setSearch}
            />
          )}
          {addEntityLabel && (
            <Button onClick={onAddEntity} disabled={addEntityDisabled}>
              <Add data-icon="inline-start" aria-hidden="true" />
              {addEntityLabel}
            </Button>
          )}
        </Flex>
      )}
      <DataTable<EntityRow<D>>
        columns={columns}
        data={tableData}
        getRowId={(row) => row.id}
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
        // Carbon collapsed into an overflow menu once `menuItems` held more
        // than two entries; the DS threshold is the *visible* count at which
        // the menu kicks in, so it also collapses at three — but now per row,
        // after `hidden` has been applied.
        inlineActionsThreshold={MAX_ICON_ACTIONS + 1}
        expansion={expansion}
        onRowClick={isEntityClickable ? handleRowClick : undefined}
        rowSelection={
          batchSelection
            ? { selectedRowIds, onSelectedRowsChange: handleSelectedRowsChange }
            : undefined
        }
      />
    </Flex>
  );
};

/**
 * `DataTableHeader.key` is constrained to columns whose value is renderable
 * (`string | ReactNode`), which is the same contract Carbon's cell renderer
 * relied on. Arrays are joined, matching the previous behaviour.
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
          className="[all:unset]"
        >{`${text.substring(0, maxLength)}…`}</button>
      </TooltipTrigger>
      {/* DS ships no width cap on tooltip content; long text would else stretch to the viewport edge. */}
      <TooltipContent className="max-w-[28rem]">{text}</TooltipContent>
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
  entity: D | undefined,
): boolean {
  if (typeof flag === "function") {
    return entity !== undefined && flag(entity);
  }
  return !!flag;
}

export default EntityList;
