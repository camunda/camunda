/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, ReactNode, useMemo, useState } from "react";
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
  TableToolbarSearch,
  Pagination,
  Tooltip,
} from "@carbon/react";
import useDebounce from "react-debounced";
import styled from "styled-components";
import { ButtonKind } from "@carbon/react/lib/components/Button/Button";
import { Add, CarbonIconType } from "@carbon/react/icons";
import { DocumentationLink } from "src/components/documentation";
import LateLoading from "src/components/layout/LateLoading";
import Flex from "src/components/layout/Flex";
import useTranslate from "src/utility/localization";
import { StyledTableContainer } from "./components";

const StyledTableCell = styled(TableCell)<{ $isClickable?: boolean }>`
  cursor: ${({ $isClickable }) => ($isClickable ? "pointer" : "auto")};
`;

const TooltipTrigger = styled.button`
  all: unset;
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
  sortProperty?: keyof D;
  loading?: boolean;
  isInsideModal?: boolean;
  title?: ReactNode;
  batchSelection?: {
    onSelect: (selected: D) => unknown;
    onUnselect: (selected: D) => unknown;
    onSelectAll: (selected: D[]) => unknown;
    isSelected: (selected: D) => boolean;
  };
};

const MAX_ICON_ACTIONS = 2;
const PAGINATION_HIDE_LIMIT = 25;
const PAGINATION_MAX_PAGE_SIZE = 15;
const MAX_DISPLAY_CELL_LENGTH = 20;

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
  sortProperty,
  loading,
  batchSelection,
  searchPlaceholder,
}: EntityListProps<D>): ReturnType<FC> => {
  const debounce = useDebounce(300);
  const { t } = useTranslate("components");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGINATION_MAX_PAGE_SIZE);

  const hasMenu = menuItems && menuItems.length > 0;

  const [index, tableData] = useMemo(() => {
    const entityIndex: { [id: string]: D } = {};
    const entityTableData: (D & { id: string })[] = [];

    data?.forEach((dataset) => {
      const id = dataset.id || (Date.now() + Math.random()).toString();
      entityIndex[id] = dataset;
      entityTableData.push({ ...dataset, id });
    });

    const sortedEntityTableData =
      entityTableData.length > 0
        ? entityTableData.sort((a, b) => {
            const propertyName =
              sortProperty || Object.keys(entityTableData[0])[0];

            if (a[propertyName] < b[propertyName]) {
              return -1;
            }
            if (a[propertyName] > b[propertyName]) {
              return 1;
            }
            return 0;
          })
        : entityTableData;

    return [entityIndex, sortedEntityTableData];
  }, [sortProperty, data]);

  const paginatedData = useMemo(() => {
    const startIndex = (page - 1) * pageSize;
    return tableData.slice(startIndex, startIndex + pageSize);
  }, [page, pageSize, tableData]);

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
    <DataTable rows={paginatedData} headers={headers} isSortable>
      {({
        rows,
        getHeaderProps,
        getToolbarProps,
        getTableProps,
        onInputChange,
      }) => (
        <StyledTableContainer {...tableContainerProps}>
          {loading && data !== null && <LateLoading />}
          {loading && data === null ? (
            <DataTableSkeleton
              columnCount={headers.length}
              headers={headers}
              showHeader={false}
              style={{ padding: 0 }}
            />
          ) : (
            <>
              <TableToolbar {...getToolbarProps()}>
                <TableToolbarContent>
                  <TableToolbarSearch
                    placeholder={searchPlaceholder}
                    persistent
                    onChange={(e) => {
                      if (e) {
                        debounce(() => {
                          onInputChange(e);
                        });
                      }
                    }}
                    onFocus={(event, handleExpand) => {
                      handleExpand(event, true);
                    }}
                    onBlur={(event, handleExpand) => {
                      const { value } = event.target;
                      if (!value) {
                        handleExpand(event, false);
                      }
                    }}
                  />
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
                    {headers.map((header) => (
                      <TableHeader
                        {...getHeaderProps({ header })}
                        key={`applications-header-${header.header}`}
                      >
                        {header.header}
                      </TableHeader>
                    ))}
                    {hasMenu && <TableExpandHeader />}
                  </TableRow>
                </TableHead>
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
                              MAX_DISPLAY_CELL_LENGTH ? (
                              <Tooltip
                                label={displayValue}
                                autoAlign
                                align="bottom"
                              >
                                <TooltipTrigger>
                                  {displayValue
                                    .substring(0, MAX_DISPLAY_CELL_LENGTH)
                                    .concat("…")}
                                </TooltipTrigger>
                              </Tooltip>
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
            </>
          )}
          {tableData.length > PAGINATION_HIDE_LIMIT && (
            <Pagination
              backwardText={t("Previous page")}
              forwardText={t("Next page")}
              itemsPerPageText={t("Items per page:")}
              page={page}
              pageNumberText={t("Page Number")}
              pageSize={pageSize}
              pageSizes={[15, 20, 30, 40, 50]}
              totalItems={tableData.length}
              onChange={({
                page,
                pageSize,
              }: {
                page: number;
                pageSize: number;
              }) => {
                setPage(page);
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
