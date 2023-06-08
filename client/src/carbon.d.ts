/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {RadioButtonSkeletonProps} from '@carbon/react';
import {
  FC,
  ComponentProps,
  ReactNode,
  RefObject,
  HTMLAttributes,
  ButtonHTMLAttributes,
  ThHTMLAttributes,
  Component,
  TableHTMLAttributes,
  ForwardRefExoticComponent,
  PropsWithoutRef,
  PropsWithChildren,
  RefAttributes,
  TdHTMLAttributes,
  ChangeEvent,
  MouseEvent,
  KeyboardEvent,
  FocusEvent,
  ReactElement,
  ChangeEventHandler,
  AnchorHTMLAttributes,
  InputHTMLAttributes,
} from 'react';

declare module '@carbon/react' {
  declare function Button(
    props: {
      /** Specify the kind of Button you want to create */
      kind?:
        | 'danger'
        | 'ghost'
        | 'primary'
        | 'secondary'
        | 'tertiary'
        | 'danger--tertiary'
        | 'danger--ghost';
      /** Specify the message read by screen readers for the danger button variant */
      dangerDescription?: string;
      /** Optionally specify an href for your Button to become an `<a>` element */
      href?: string;
      /** Specify whether the Button is expressive, or not */
      isExpressive?: boolean;
      /** Specify whether the Button is currently selected. Only applies to the Ghost variant. */
      isSelected?: boolean;
      /** Optional prop to allow overriding the icon rendering. Can be a React component class */
      renderIcon?: ReactNode;
      /** Optional prop to specify the role of the Button */
      role?: string;
      /** Specify the size of the button */
      size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
      /** Specify the alignment of the tooltip to the icon-only button. Can be one of: start, center, or end. */
      tooltipPosition?: 'top' | 'bottom' | 'left' | 'right';
      /** Specify the direction of the tooltip for icon-only buttons. Can be either top, right, bottom, or left. */
      tooltipAlignment?: 'start' | 'center' | 'end';
    } & (
      | {
          /** Specify if the button is an icon-only button */
          hasIconOnly: true;
          /** If specifying the renderIcon prop, provide a description for that icon that can be read by screen readers */
          iconDescription: string;
        }
      | {hasIconOnly?: false; iconDescription?: never}
    ) &
      ComponentProps<'button'>
  );

  declare function ComposedModal(
    props: {
      /** Specify whether the Modal is currently open */
      open?: boolean;
      /** Specify a handler for closing modal. The handler should care of closing modal, e.g. changing `open` prop. */
      onClose?: () => void;
      /** Specify the size variant. */
      size?: 'xs' | 'sm' | 'md' | 'lg';
      /** Specify whether or not the Modal content should have any inner padding. */
      isFullWidth?: boolean;
      /** Specify an optional className to be applied to the modal node */
      containerClassName?: string;
      /** Specify whether the Modal is for dangerous actions */
      danger?: boolean;
      /** Prevent closing on click outside of modal */
      preventCloseOnClickOutside?: boolean;
      /** Specify a CSS selector that matches the DOM element that should be focused when the Modal opens */
      selectorPrimaryFocus?: string;
      /** Specify CSS selectors that match DOM elements working as floating menus. Focusing on those elements won't trigger "focus-wrap" behavior */
      selectorsFloatingMenus?: string[];
      /** Specify if Enter key should be used as "submit" action */
    } & ComponentProps<'div'>
  );

  declare function ModalBody(
    props: {
      /** Provide whether the modal content has a form element. If true is used here, non-form child content should have bx--modal-content__regular-content class. */
      hasForm?: boolean;
      /** Specify whether the modal contains scrolling content */
      hasScrollingContent?: boolean;
    } & ComponentProps<'div'>
  );

  declare function ModalHeader(
    props: {
      /** Provide an optional function to be called when the close button is clicked */
      buttonOnClick?: () => void;
      /** Specify an optional className to be applied to the modal close node */
      closeClassName?: string;
      /** Specify an optional className to be applied to the modal close node */
      closeIconClassName?: string;
      /** Provide an optional function to be called when the modal is closed */
      closeModal?: () => void;
      /** Specify a description for the close icon that can be read by screen readers */
      iconDescription?: string;
      /** Specify an optional label to be displayed */
      label?: ReactNode;
      /** Specify an optional className to be applied to the modal header label */
      labelClassName?: string;
      /** Specify an optional title to be displayed */
      titile?: ReactNode;
      /** Specify an optional className to be applied to the modal heading */
      titleClassName?: string;
    } & ComponentProps<'div'>
  );

  declare function ModalFooter(
    props: {
      /** Specify an optional function that is called whenever the modal is closed */
      closeModal?: () => void;
      /** Specify whether the primary button should be replaced with danger button. Note that this prop is not applied if you render primary/danger button by yourself */
      danger?: boolean;
      /** The `ref` callback for the primary button. */
      inputref?: RefObject<HTMLElement>;
      /** Specify an optional function for when the modal is requesting to be closed */
      onRequestClose?: () => void;
      /** Specify an optional function for when the modal is requesting to be submitted */
      onRequestSubmit?: () => void;
      /** Specify whether the primary button should be disabled */
      primaryButtonDisabled?: boolean;
      /** Specify the text for the primary button */
      primaryButtonText?: string;
      /** Specify a custom className to be applied to the primary button */
      primaryClassName?: string;
      /** Specify an array of config objects for secondary buttons */
      secondaryButtons?: {buttonText: string; onClick: () => void}[];
      /** Specify the text for the secondary button */
      secondaryButtonText?: string;
      /** Specify a custom className to be applied to the secondary button */
      secondaryClassName?: string;
    } & ComponentProps<'div'>
  );

  export type ShapeOf<B extends object, E extends object = {[key: string]: any}> = (E extends never
    ? {}
    : E) &
    B;
  export interface ReactAttr<T = HTMLElement> extends HTMLAttributes<T> {}
  export interface ReactButtonAttr<T = HTMLButtonElement> extends ButtonHTMLAttributes<T> {}
  export interface ReactDivAttr extends ReactAttr<HTMLDivElement> {}
  export interface ReactAnchorAttr<T = HTMLAnchorElement> extends AnchorHTMLAttributes<T> {}
  export interface ReactInputAttr<T = HTMLInputElement> extends InputHTMLAttributes<T> {}
  export type ForwardRefProps<T, P = {}> = PropsWithoutRef<PropsWithChildren<P>> & RefAttributes<T>;
  export type ForwardRefReturn<T, P = {}> = ForwardRefExoticComponent<ForwardRefProps<T, P>>;
  export interface RequiresIdProps<T = ReactAttr['id']> {
    id: NonNullable<T>;
  }

  export type VerticalDirection = 'bottom' | 'top';

  export interface InternationalProps<MID = string, ARGS = Record<string, unknown>> {
    translateWithId?(messageId: MID, args?: ARGS): string;
  }

  export type FCReturn = ReturnType<FC>;

  export interface MenuOffsetData {
    left?: number | undefined;
    top?: number | undefined;
  }

  export interface RequiresChildrenProps<T = ReactNode> {
    children: NonNullable<T>;
  }

  export interface DataTableCell<V = any, H extends DataTableHeader = DataTableHeader> {
    errors?: any[] | null | undefined;
    id: string;
    info: {
      header: H['key'];
    };
    isEditable: boolean;
    isEditing: boolean;
    isValid: boolean;
    value?: V | undefined;
  }

  export interface DataTableRow<ID extends string = string> {
    disabled?: boolean | undefined;
    id: ID;
    isExpanded?: boolean | undefined;
    isSelected?: boolean | undefined;
  }

  export type DenormalizedRow = DataTableRow & {cells: DataTableCell[]};

  export interface DataTableHeader<K extends string = string> {
    header: NonNullable<ReactNode>;
    key: K;
  }

  export interface DataTableCustomHeaderData<H extends DataTableHeader = DataTableHeader> {
    header: H;
    isSortable?: boolean | undefined;
    onClick?(event: MouseEvent<HTMLElement>): void;
  }

  export interface DataTableCustomHeaderProps<H extends {key: string} = DataTableHeader> {
    isSortable?: boolean | undefined;
    isSortHeader: boolean;
    key: H['key'];
    onClick(event: MouseEvent<HTMLElement>): void;
    sortDirection: DataTableSortState;
  }

  export interface DataTableCustomBatchActionsData {}

  export interface DataTableCustomBatchActionsProps {
    onCancel(): void;
    shouldShowBatchActions?: boolean | undefined;
    totalSelected: number;
  }

  export interface DataTableCustomRowData<R extends DataTableRow = DataTableRow> {
    onClick?(event: MouseEvent<HTMLElement>): void;
    row: R;
  }

  export interface DataTableCustomRowProps<R extends DataTableRow = DataTableRow> {
    ariaLabel: string;
    disabled: Exclude<R['disabled'], undefined>;
    isExpanded: Exclude<R['isExpanded'], undefined>;
    isSelected: Exclude<R['isSelected'], undefined>;
    key: R['id'];
    onExpand(event: MouseEvent<HTMLElement>): void;
  }

  export interface DataTableCustomSelectionData<R extends DataTableRow = DataTableRow> {
    onClick?(event: MouseEvent<HTMLElement>): void;
    onExpand(event: MouseEvent<HTMLElement>): void;
    row?: R | undefined;
  }

  export interface DataTableCustomRenderProps<
    R extends DataTableRow = DataTableRow,
    H extends DataTableHeader = DataTableHeader
  > {
    expandAll(): void;
    expandRow(rowId: R['id']): void;
    getBatchActionProps<E extends object = ReactDivAttr>(
      data?: ShapeOf<DataTableCustomBatchActionsData, E>
    ): ShapeOf<DataTableCustomBatchActionsProps, E>;
    getExpandHeaderProps(props?: TableExpandHeaderProps): TableExpandHeaderProps;
    getHeaderProps<E extends object = ReactAttr>(
      data: ShapeOf<DataTableCustomHeaderData<H>, E>
    ): ShapeOf<DataTableCustomHeaderProps<H>, E>;
    getRowProps<E extends object = ReactAttr<HTMLTableRowElement>>(
      data: ShapeOf<DataTableCustomRowData, E>
    ): ShapeOf<DataTableCustomRowProps, E>;
    getSelectionProps<E extends object = {}>(
      data?: ShapeOf<DataTableCustomSelectionData, E>
    ):
      | ShapeOf<DataTableCustomSelectionProps<R>, E>
      | ShapeOf<DataTableCustomSelectionProps<never>, E>;
    getTableContainerProps(): Pick<TableContainerProps, 'stickyHeader' | 'useStaticWidth'>;
    getTableProps(): TableCarbonProps;
    getToolbarProps(props?: TableToolbarProps): TableToolbarProps;
    headers: DataTableProps<R, H>['headers'];
    onInputChange(event: SyntheticEvent<HTMLInputElement>): void;
    radio?: DataTableProps<R, H>['radio'] | undefined;
    rows: ReadonlyArray<DenormalizedRow>;
    selectAll(): void;
    selectedRows: ReadonlyArray<DenormalizedRow>;
    selectRow(rowId: R['id']): void;
    sortBy(headerKey: H['key']): void;
  }

  export interface DataTableProps<
    R extends DataTableRow = DataTableRow,
    H extends DataTableHeader = DataTableHeader
  > extends TableCarbonProps {
    filterRows?(data: FilterRowsData<R, H>): Array<R['id']>;
    headers: H[];
    locale?: string | undefined;
    radio?: boolean | undefined;
    render?(props: DataTableCustomRenderProps<R, H>): ReactNode;
    rows: R[];
    sortRow?(cellA: any, cellB: any, data: SortRowData): number;
    stickyHeader?: boolean | undefined;
  }

  declare class DataTable<
    R extends DataTableRow = DataTableRow,
    H extends DataTableHeader = DataTableHeader
  > extends Component<DataTableProps<R, H>> {}

  interface InheritedProps extends TableHTMLAttributes<HTMLTableElement> {}

  export type DataTableSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

  export interface TableCarbonProps {
    isSortable?: boolean | undefined;
    overflowMenuOnHover?: boolean | undefined;
    size?: DataTableSize | undefined;
    useStaticWidth?: boolean | undefined;
    useZebraStyles?: boolean | undefined;
  }

  export interface DataTableSkeletonHeader {
    header?: string | undefined;
  }

  export interface DataTableSkeletonProps extends TableHTMLAttributes<HTMLTableElement> {
    compact?: boolean | undefined;
    columnCount?: number | undefined;
    headers?: readonly DataTableSkeletonHeader[] | undefined;
    rowCount?: number | undefined;
    showHeader?: boolean | undefined;
    showToolbar?: boolean | undefined;
    zebra?: boolean | undefined;
  }

  declare const DataTableSkeleton: FC<DataTableSkeletonProps>;

  export interface TableProps extends InheritedProps, TableCarbonProps {}

  declare const Table: FC<TableProps>;

  export interface TableContainerProps extends Omit<ReactDivAttr, 'title'> {
    description?: ReactNode | undefined;
    stickyHeader?: boolean | undefined;
    useStaticWidth?: boolean | undefined;
    title?: ReactNode | undefined;
  }

  declare const TableContainer: FC<TableContainerProps>;

  export interface TableHeadProps extends ReactAttr<HTMLTableSectionElement> {}

  declare const TableHead: FC<TableHeadProps>;

  export type DataTableSortState = 'ASC' | 'DESC' | 'NONE';

  export type DataTableSortStates = Readonly<{
    ASC: Extract<DataTableSortState, 'ASC'>;
    DESC: Extract<DataTableSortState, 'DESC'>;
    NONE: Extract<DataTableSortState, 'NONE'>;
  }>;

  export declare const sortStates: DataTableSortStates;
  export declare const initialSortState: Extract<DataTableSortState, 'NONE'>;

  export interface TableHeaderProps
    extends ReactButtonAttr<HTMLElement>,
      ThHTMLAttributes<HTMLElement> {
    isSortable?: boolean | undefined;
    isSortHeader?: boolean | undefined;
    sortDirection?: DataTableSortState | undefined;
  }

  interface TableHeaderFC extends ForwardRefReturn<HTMLTableHeaderCellElement, TableHeaderProps> {
    readonly translationKeys: ReadonlyArray<TableHeaderTranslationKey>;
  }

  declare const TableHeader: TableHeaderFC;

  export interface TableBodyProps extends ReactAttr<HTMLTableSectionElement> {}

  declare const TableBody: FC<TableBodyProps>;

  export interface TableRowProps extends ReactAttr<HTMLTableRowElement> {
    isSelected?: boolean | undefined;
  }

  declare const TableRow: FC<TableRowProps>;

  export interface TableCellProps extends TdHTMLAttributes<HTMLTableDataCellElement> {}

  declare const TableCell: FC<TableCellProps>;

  export interface PaginationPageSize {
    text: string;
    value: string;
  }

  export interface PaginationProps extends Omit<ReactDivAttr, 'id' | 'onChange'> {
    backwardText?: string | undefined;
    forwardedRef?: ForwardedRef<HTMLDivElement>;
    forwardText?: string | undefined;
    id?: number | string | undefined;
    isLastPage?: boolean | undefined;
    itemsPerPageText?: string | undefined;
    itemRangeText?(min: number, max: number, total: number): string;
    itemText?(min: number, max: number): string;
    onChange(data: {page: number; pageSize: number}): void;
    page?: number | undefined;
    pageInputDisabled?: boolean | undefined;
    pageNumberText?: string | undefined;
    pageRangeText?(current: number, total: number): string;
    pageSize?: number | undefined;
    pageSizeInputDisabled?: boolean | undefined;
    pageSizes: readonly number[] | readonly PaginationPageSize[];
    pageText?(page: number): string;
    pagesUnknown?: boolean | undefined;
    size?: 'sm' | 'md' | 'lg' | undefined;
    totalItems?: number | undefined;
  }

  declare class Pagination extends Component<PaginationProps> {}

  export interface TableSelectAllProps {
    ariaLabel?: InlineCheckboxProps['ariaLabel'] | undefined; // required but has default value
    checked: NonNullable<InlineCheckboxProps['checked']>;
    className?: ReactAttr['className'] | undefined;
    disabled?: InlineCheckboxProps['disabled'] | undefined;
    id: NonNullable<InlineCheckboxProps['id']>;
    indeterminate?: InlineCheckboxProps['indeterminate'] | undefined;
    name: NonNullable<InlineCheckboxProps['name']>;
    onSelect: InlineCheckboxProps['onClick'];
  }

  declare const TableSelectAll: FC<TableSelectAllProps>;

  export interface InlineCheckboxProps
    extends Omit<ReactInputAttr, 'aria-label' | 'className' | 'id' | 'onChange' | 'ref' | 'type'>,
      RequiresIdProps {
    ariaLabel?: ReactInputAttr['aria-label'] | undefined;
    indeterminate?: boolean | undefined;
    onChange?(checked: boolean, id: string, event: ChangeEvent<HTMLInputElement>): void;
  }

  export type TableSelectRowOnChange = (
    value: RadioButtonSkeletonProps['value'] | InlineCheckboxProps['checked'],
    idOrName: RadioButtonProps['name'] | InlineCheckboxProps['id'],
    evt: ChangeEvent<HTMLInputElement>
  ) => void;

  export interface TableSelectRowProps {
    ariaLabel?: string | undefined;
    checked: boolean;
    className?: string | undefined;
    disabled?: boolean | undefined;
    id: string;
    name: string;
    onChange?: TableSelectRowOnChange | undefined;
    onSelect(event: MouseEvent<HTMLInputElement>): void;
    radio?: boolean | undefined;
  }

  declare const TableSelectRow: FC<TableSelectRowProps>;

  type GetMenuOffsetFn = (
    menuBody: HTMLElement,
    direction: Direction,
    trigger?: HTMLElement,
    flip?: boolean
  ) => MenuOffsetData | undefined;
  export declare const getMenuOffset: GetMenuOffsetFn;

  export type MenuOffsetValue = MenuOffsetData | GetMenuOffsetFn;

  export interface MenuProps {
    children?: ReactNode | undefined;
    className?: string | undefined;
    id?: string | undefined;
    level?: number | undefined;
    onClose?(): void;
    onKeyDown?(evt: KeyboardEvent<HTMLUListElement>): void;
    open?: boolean | undefined;
    size?: 'sm' | 'md' | 'lg' | undefined;
    target?: Element | undefined;
    x?: number | readonly number[] | undefined;
    y?: number | readonly number[] | undefined;
  }

  export interface OverflowMenuProps
    extends Omit<
      ReactButtonAttr,
      'aria-expanded' | 'aria-haspopup' | 'aria-label' | 'onClick' | 'onKeyDown' | 'type'
    > {
    ariaLabel?: string | undefined;
    direction?: VerticalDirection | undefined;
    iconClass?: ReactAttr['className'] | undefined;
    iconDescription?: string | undefined;
    flipped?: boolean | undefined;
    focusTrap?: boolean | undefined;
    menuOffset?: MenuOffsetValue | undefined;
    menuOffsetFlip?: MenuOffsetValue | undefined;
    menuOptionsClass?: ReactAttr['className'] | undefined;
    onClick?(event: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>): void;
    onClose?(): void;
    onOpen?(): void;
    open?: boolean | undefined;
    renderIcon?: any;
    selectorPrimaryFocus?: string | undefined;
    size?: MenuProps['size'];
  }

  declare class OverflowMenuComponent extends Component<OverflowMenuProps> {}

  declare const OverflowMenu: ForwardRefReturn<HTMLButtonElement, OverflowMenuProps>;

  interface OverFlowMenuItemSharedProps {
    // closeMenu is supplied by Overflow parent component
    disabled?: boolean | undefined;
    hasDivider?: boolean | undefined;
    isDelete?: boolean | undefined;
    itemText: NonNullable<ReactNode>;
    requireTitle?: boolean | undefined;
    wrapperClassName?: string | undefined;
  }

  export interface OverflowMenuItemButtonProps
    extends Omit<ReactButtonAttr, 'disabled' | 'href' | 'ref' | 'tabIndex'>,
      OverFlowMenuItemSharedProps {
    href?: null | undefined;
  }

  export interface OverflowMenuItemAnchorProps
    extends Omit<ReactAnchorAttr, 'disabled' | 'href' | 'ref' | 'tabIndex'>,
      OverFlowMenuItemSharedProps {
    href: string;
  }

  export type AllOverflowMenuItemProps = OverflowMenuItemAnchorProps | OverflowMenuItemButtonProps;

  declare class OverflowMenuItem extends Component<AllOverflowMenuItemProps> {}

  export interface TableToolbarProps extends ReactAttr {
    size?: 'lg' | 'normal' | 'sm' | 'small' | undefined;
  }

  declare const TableToolbar: FC<TableToolbarProps>;

  export interface TableToolbarActionAnchorProps
    extends Omit<OverflowMenuItemAnchorProps, 'children' | 'itemText'>,
      RequiresChildrenProps {
    itemText?: ReactNode | undefined;
  }

  export interface TableToolbarActionButtonProps
    extends Omit<OverflowMenuItemButtonProps, 'children' | 'itemText'>,
      RequiresChildrenProps {
    itemText?: ReactNode | undefined;
  }

  export type AllTableToolbarActionProps =
    | TableToolbarActionAnchorProps
    | TableToolbarActionButtonProps;

  declare function TableToolbarAction(
    props: ForwardRefProps<HTMLAnchorElement, TableToolbarActionAnchorProps>
  ): FCReturn;

  declare function TableToolbarAction(
    props: ForwardRefProps<HTMLButtonElement, TableToolbarActionButtonProps>
  ): FCReturn;

  export interface TableToolbarContentProps extends ReactDivAttr {}

  declare const TableToolbarContent: FC<TableToolbarContentProps>;

  export interface TableToolbarMenuProps
    extends Omit<OverflowMenuProps, 'children'>,
      RequiresChildrenProps {}

  declare const TableToolbarMenu: FC<TableToolbarMenuProps>;

  export interface SearchElementProps
    extends Omit<ReactInputAttr, 'defaultValue' | 'ref' | 'size' | 'value' | 'onChange'> {
    closeButtonLabelText?: string | undefined;
    defaultValue?: string | number | undefined;
    labelText: NonNullable<ReactNode>;
    onClear?: () => void;
    renderIcon?: ReactElement | undefined; // code calls React.cloneElement so it can only be an element.
    size?: 'sm' | 'md' | 'lg' | 'xl' | undefined;
    value?: string | number | undefined;
    light?: boolean | undefined;
    onChange?: ChangeEventHandler<HTMLInputElement>;
  }

  declare class Search extends Component<SearchElementProps> {}

  export type TableToolbarTranslationKey =
    | 'carbon.table.toolbar.search.label'
    | 'carbon.table.toolbar.search.placeholder';

  export type TableToolbarSearchHandleExpand = (
    event: FocusEvent<HTMLInputElement>,
    newValue?: boolean
  ) => void;

  export interface TableToolbarSearchProps
    extends Omit<SearchElementProps, 'labelText' | 'onBlur' | 'onFocus'>,
      InternationalProps<TableToolbarTranslationKey> {
    defaultExpanded?: boolean | undefined;
    expanded?: boolean | undefined;
    labelText?: ReactNode | undefined;
    onBlur?(
      event: FocusEvent<HTMLInputElement>,
      handleExpand: TableToolbarSearchHandleExpand
    ): void;
    onExpand?(event: FocusEvent<HTMLInputElement>, newExpand: boolean): void;
    onFocus?(
      event: FocusEvent<HTMLInputElement>,
      handleExpand: TableToolbarSearchHandleExpand
    ): void;
    persistent?: boolean | undefined;
    searchContainerClass?: string | undefined;
  }

  declare const TableToolbarSearch: FC<TableToolbarSearchProps>;
}

// export {};
