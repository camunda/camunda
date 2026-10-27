/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Menu} from '@carbon/react';
import {
  ComponentProps,
  ReactNode,
  HTMLAttributes,
  ButtonHTMLAttributes,
  Component,
  ForwardRefExoticComponent,
  PropsWithoutRef,
  PropsWithChildren,
  RefAttributes,
  ChangeEvent,
  MouseEvent,
  KeyboardEvent,
  InputHTMLAttributes,
} from 'react';

declare module '@carbon/react' {
  export type ShapeOf<
    B extends object,
    E extends object = {[key: string]: unknown},
  > = (E extends never ? object : E) & B;
  export type ForwardRefProps<T, P = object> = PropsWithoutRef<PropsWithChildren<P>> &
    RefAttributes<T>;
  export type ForwardRefReturn<T, P = object> = ForwardRefExoticComponent<ForwardRefProps<T, P>>;
  export interface RequiresIdProps<T = HTMLAttributes['id']> {
    id: NonNullable<T>;
  }

  export type VerticalDirection = 'bottom' | 'top';

  export interface MenuOffsetData {
    left?: number | undefined;
    top?: number | undefined;
  }

  export interface PaginationProps extends Omit<HTMLAttributes<HTMLDivElement>, 'id' | 'onChange'> {
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

  export interface InlineCheckboxProps
    extends Omit<
        InputHTMLAttributes<HTMLInputElement>,
        'aria-label' | 'className' | 'id' | 'onChange' | 'ref' | 'type'
      >,
      RequiresIdProps {
    ariaLabel?: InputHTMLAttributes<HTMLInputElement>['aria-label'] | undefined;
    indeterminate?: boolean | undefined;
    onChange?(checked: boolean, id: string, event: ChangeEvent<HTMLInputElement>): void;
  }

  type GetMenuOffsetFn = (
    menuBody: HTMLElement,
    direction: Direction,
    trigger?: HTMLElement,
    flip?: boolean
  ) => MenuOffsetData | undefined;
  export declare const getMenuOffset: GetMenuOffsetFn;

  export type MenuOffsetValue = MenuOffsetData | GetMenuOffsetFn;

  export interface OverflowMenuProps
    extends Omit<
      ButtonHTMLAttributes,
      'aria-expanded' | 'aria-haspopup' | 'aria-label' | 'onClick' | 'onKeyDown' | 'type'
    > {
    ariaLabel?: string | undefined;
    direction?: VerticalDirection | undefined;
    iconClass?: HTMLAttributes['className'] | undefined;
    iconDescription?: string | undefined;
    flipped?: boolean | undefined;
    focusTrap?: boolean | undefined;
    menuOffset?: MenuOffsetValue | undefined;
    menuOffsetFlip?: MenuOffsetValue | undefined;
    menuOptionsClass?: HTMLAttributes['className'] | undefined;
    onClick?(event: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>): void;
    onClose?(): void;
    onOpen?(): void;
    open?: boolean | undefined;
    renderIcon?: ReactNode;
    selectorPrimaryFocus?: string | undefined;
    size?: ComponentProps<typeof Menu>['size'];
  }

  declare const OverflowMenu: ForwardRefReturn<HTMLButtonElement, OverflowMenuProps>;

  interface PasswordInputProps extends InputHTMLAttributes<HTMLInputElement> {
    defaultValue?: string | number;
    helperText?: ReactNode;
    hideLabel?: boolean;
    hidePasswordLabel?: string;
    id: string;
    inline?: boolean;
    invalid?: boolean;
    invalidText?: ReactNode;
    labelText: ReactNode;
    onTogglePasswordVisibility?: () => void;
    showPasswordLabel?: string;
    size?: 'sm' | 'md' | 'lg';
    tooltipAlignment?: 'start' | 'center' | 'end';
    tooltipPosition?: 'top' | 'right' | 'bottom' | 'left';
    value?: string | number;
    warn?: boolean;
    warnText?: ReactNode;
  }

  declare function PasswordInput(
    props: ForwardRefProps<HTMLInputElement, PasswordInputProps>
  ): JSX.Element;

  interface MenuButtonProps {
    children: ReactNode;
    className?: string;
    disabled?: boolean;
    kind?: 'primary' | 'tertiary' | 'ghost';
    label: string;
    size?: 'sm' | 'md' | 'lg';
    menuAlignment?: 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end' | 'bottom' | 'top';
  }

  declare function Layer(
    props: ForwardRefProps<
      HTMLElement,
      {
        as?: ElementType;
        children?: ReactNode;
        className?: string;
        level?: number;
      }
    >
  ): JSX.Element;

  declare function ProgressIndicator(
    props: ForwardRefProps<
      HTMLElement,
      {
        children?: ReactNode;
        className?: string;
        currentIndex?: number;
        onChange?: (evt: UIEvent<HTMLElement>) => void;
        spaceEqually?: boolean;
        vertical?: boolean;
      }
    >
  ): JSX.Element;

  declare function ProgressStep(
    props: ForwardRefProps<
      HTMLElement,
      {
        className?: string;
        index?: number;
        onChange?: (evt: UIEvent<HTMLElement>) => void;
        current?: boolean;
        complete?: boolean;
        description?: string;
        disabled?: boolean;
        invalid?: boolean;
        label: string;
        secondaryLabel?: string;
      }
    >
  ): JSX.Element;

  declare function InlineLoading(
    props: ForwardRefProps<
      HTMLElement,
      {
        className?: string;
        description?: ReactNode;
        iconDescription?: string;
        onSuccess?: () => void;
        status?: string;
        successDelay?: number;
      }
    >
  ): JSX.Element;
}
