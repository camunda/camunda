/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode, useMemo } from "react";
import {
  Button,
  DataTable,
  DataTableSkeleton,
  Link,
  OverflowMenu,
  OverflowMenuItem,
  Table,
  TableBody,
  TableCell,
  TableExpandHeader,
  TableHead,
  TableHeader,
  TableRow,
  TableSelectAll,
  TableSelectRow,
  TableToolbar,
  TableToolbarContent,
  Pagination,
  Tooltip,
} from "@carbon/react";
import styled from "styled-components";
import { ButtonKind } from "@carbon/react/lib/components/Button/Button";
import { Add, CarbonIconType } from "@carbon/react/icons";
import { DocumentationLink } from "src/components/documentation";
import Flex from "src/components/layout/Flex";
import useTranslate from "src/utility/localization";
import { StyledTableContainer } from "./components";
import { PageResult, SortConfig } from "src/utility/api";
import SearchBar from "./SearchBar";

const StyledTableCell = styled(TableCell)<{ $isClickable?: boolean }>`
  cursor: ${({ $isClickable }) => ($isClickable ? "pointer" : "auto")};
`;

const TooltipTrigger = styled.button`
  all: unset;
`;

const StyledToolTip = styled(Tooltip)`
  .cds--tooltip-content {
    max-inline-size: 28rem;
  }
`;

export type EntityData = {
  [key: string]: string | object | boolean | number;
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
  disabled?: boolean;
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
};

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
}: EntityListProps<D>): ReturnType<FC> => {
  const { t } = useTranslate("components");

  const hasMenu = menuItems && menuItems.length > 0;

  const [index, tableData] = useMemo(() => {
    const entityIndex: { [id: string]: D } = {};
    const entityTableData: (D & { id: string })[] = [];

    data?.forEach((dataset) => {
      const id = dataset.id || (Date.now() + Math.random()).toString();
      entityIndex[id] = dataset;
      entityTableData.push({ ...dataset, id });
    });

    return [entityIndex, entityTableData];
  }, [data]);

  const areRowsEmpty = !tableData || tableData.length === 0;

  const isEntityClickable = onEntityClick !== undefined;

  const handleEntityClick = (id: string) => () => {
    const textSelection = window.getSelection();

    if (
      isEntityClickable &&
      (!textSelection || textSelection.toString().length === 0)
    )
      onEntityClick(index[id]);
  };

  const handleMenuItemClick =
    (id: string, onClick: MenuItem<D>["onClick"]) => () =>
      onClick(index[id]);

  const selectedLength =
    (batchSelection && data?.filter(batchSelection.isSelected).length) || 0;

  const tableContainerProps = isInsideModal
    ? { $compact: true }
    : {
        title,
        description: (
          <>
            {description && <p>{description}</p>}
            {documentationPath && (
              <DocumentationLink path={documentationPath}>
                {t("Learn more")}
              </DocumentationLink>
            )}
          </>
        ),
      };

  return (
    <DataTable
      rows={tableData}
      headers={headers}
      isSortable
      sortRow={() => {
        return 0;
      }}
    >
      {({ rows, getHeaderProps, getToolbarProps, getTableProps }) => (
        <StyledTableContainer {...tableContainerProps}>
          <>
            {(searchKey || addEntityLabel) && (
              <TableToolbar {...getToolbarProps()}>
                <TableToolbarContent>
                  {searchKey && (
                    <SearchBar
                      searchKey={searchKey}
                      searchPlaceholder={searchPlaceholder}
                      onSearch={setSearch}
                    />
                  )}
                  {addEntityLabel && (
                    <Button
                      renderIcon={Add}
                      onClick={onAddEntity}
                      disabled={addEntityDisabled}
                    >
                      {addEntityLabel}
                    </Button>
                  )}
                </TableToolbarContent>
              </TableToolbar>
            )}
            {loading && (
              <DataTableSkeleton
                columnCount={headers.length}
                headers={headers}
                showHeader={false}
                showToolbar={false}
                style={{ padding: 0 }}
              />
            )}

            {!loading && (
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {batchSelection && (
                      <TableSelectAll
                        id="select-all"
                        name="select-all"
                        checked={selectedLength === data?.length}
                        indeterminate={
                          selectedLength > 0 && selectedLength !== data?.length
                        }
                        onSelect={() => {
                          if (data) {
                            if (selectedLength === data.length) {
                              batchSelection.onSelectAll([]);
                            } else {
                              batchSelection.onSelectAll(data);
                            }
                          }
                        }}
                      />
                    )}
                    {headers.map((header) => {
                      return (
                        <TableHeader
                          {...getHeaderProps({
                            header: header,
                            isSortable: !!header.isSortable,
                            onClick: (_, { sortHeaderKey, sortDirection }) => {
                              if (sortDirection === "NONE") {
                                setSort(undefined);
                                return;
                              }

                              setSort([
                                {
                                  field: sortHeaderKey,
                                  order: sortDirection,
                                },
                              ]);
                            },
                          })}
                          key={`applications-header-${header.header}`}
                        >
                          {header.header}
                        </TableHeader>
                      );
                    })}
                    {hasMenu && <TableExpandHeader />}
                  </TableRow>
                </TableHead>
                {areRowsEmpty && !loading && (
                  <TableBody>
                    <TableRow>
                      <StyledTableCell colSpan={headers.length + 1}>
                        {t("No results found")}
                      </StyledTableCell>
                    </TableRow>
                  </TableBody>
                )}
                {!areRowsEmpty && (
                  <TableBody>
                    {rows.map(({ id: rowId, cells }) => (
                      <TableRow key={rowId}>
                        {batchSelection && (
                          <TableSelectRow
                            id={`select-${rowId}`}
                            name={`select-${rowId}`}
                            checked={batchSelection.isSelected(index[rowId])}
                            onSelect={() => {
                              const item = index[rowId];

                              if (batchSelection.isSelected(item)) {
                                batchSelection.onUnselect(item);
                              } else {
                                batchSelection.onSelect(item);
                              }
                            }}
                          />
                        )}
                        {cells.map(({ id: cellId, value }, index) => {
                          const displayValue = Array.isArray(value)
                            ? value.join(", ")
                            : value;

                          const truncatedValue =
                            displayValue &&
                            displayValue.toString().length >
                              maxDisplayCellLength ? (
                              <StyledToolTip
                                label={displayValue}
                                autoAlign
                                align="bottom"
                              >
                                <TooltipTrigger>
                                  {displayValue
                                    .substring(0, maxDisplayCellLength)
                                    .concat("…")}
                                </TooltipTrigger>
                              </StyledToolTip>
                            ) : (
                              displayValue
                            );

                          return (
                            <StyledTableCell
                              key={cellId}
                              onClick={handleEntityClick(rowId)}
                              $isClickable={isEntityClickable}
                            >
                              {index === 0 && isEntityClickable ? (
                                <Link>{displayValue}</Link>
                              ) : (
                                truncatedValue
                              )}
                            </StyledTableCell>
                          );
                        })}
                        {hasMenu && (
                          <TableCell>
                            {menuItems?.length > MAX_ICON_ACTIONS ? (
                              <OverflowMenu flipped>
                                {getVisibleMenuItems(menuItems).map(
                                  ({ label, onClick, isDangerous }) => (
                                    <OverflowMenuItem
                                      key={`${label}-${rowId}`}
                                      itemText={<p>{label}</p>}
                                      isDelete={isDangerous}
                                      onClick={handleMenuItemClick(
                                        rowId,
                                        onClick,
                                      )}
                                    />
                                  ),
                                )}
                              </OverflowMenu>
                            ) : (
                              <Flex>
                                {getVisibleMenuItems(menuItems).map(
                                  (menuItem) => {
                                    const {
                                      label,
                                      onClick,
                                      icon,
                                      isDangerous,
                                      disabled,
                                    } = menuItem as MenuItem<D>;

                                    const kind: ButtonKind = isDangerous
                                      ? "danger--ghost"
                                      : "ghost";
                                    const hasIconOnly = !!icon && !isDangerous;

                                    return (
                                      <Button
                                        key={`${label}-${rowId}`}
                                        kind={kind}
                                        size="md"
                                        disabled={disabled}
                                        hasIconOnly={hasIconOnly}
                                        renderIcon={icon}
                                        tooltipAlignment="end"
                                        iconDescription={label}
                                        onClick={handleMenuItemClick(
                                          rowId,
                                          onClick,
                                        )}
                                      >
                                        {hasIconOnly ? "" : label}
                                      </Button>
                                    );
                                  },
                                )}
                              </Flex>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>
            )}
          </>
          {!!pageData?.totalItems &&
            pageData.totalItems > Math.min(...pageSizes) && (
              <Pagination
                backwardText={t("Previous page")}
                forwardText={t("Next page")}
                itemsPerPageText={t("Items per page:")}
                page={pageData.pageNumber}
                pageNumberText={t("Page Number")}
                pageSize={pageData.pageSize}
                pageSizes={pageSizes}
                totalItems={pageData.totalItems}
                onChange={({
                  page,
                  pageSize,
                }: {
                  page: number;
                  pageSize: number;
                }) => {
                  setPageNumber(page);
                  setPageSize(pageSize);
                }}
              />
            )}
        </StyledTableContainer>
      )}
    </DataTable>
  );
};

function getVisibleMenuItems<D>(
  menuItems?: (MenuItem<D> | TextMenuItem<D>)[],
): (MenuItem<D> | TextMenuItem<D>)[] {
  return menuItems ? menuItems.filter((item) => !item.hidden) : [];
}

export default EntityList;
