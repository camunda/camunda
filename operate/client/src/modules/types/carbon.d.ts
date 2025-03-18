/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type PolymorphicRef<C extends React.ElementType> =
  React.ComponentPropsWithRef<C>['ref'];

type AsProp<C extends React.ElementType> = {
  as?: C;
};

type PropsToOmit<C extends React.ElementType, P> = keyof (AsProp<C> & P);

type PolymorphicComponentProp<
  C extends React.ElementType,
  Props = {},
> = React.PropsWithChildren<Props & AsProp<C>> &
  Omit<React.ComponentPropsWithoutRef<C>, PropsToOmit<C, Props>>;

type PolymorphicComponentPropWithRef<
  C extends React.ElementType,
  Props = {},
> = PolymorphicComponentProp<C, Props> & {ref?: PolymorphicRef<C>};

declare module '@carbon/react' {
  export const Theme: React.FunctionComponent<{
    children: React.ReactNode;
    theme?: 'white' | 'g10' | 'g90' | 'g100';
    className?: string;
  }>;

  type StackProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        gap?: 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13;
        orientation?: 'horizontal' | 'vertical';
      }
    >;

  export const Stack: <C extends React.ElementType = 'div'>(
    props: StackProps<C>,
  ) => React.ReactElement | null;

  type LayerProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        children?: React.ReactNode;
        className?: string;
        level?: 0 | 1 | 2;
      }
    >;

  export const Layer: <C extends React.ElementType = 'div'>(
    props: LayerProps<C>,
  ) => React.ReactElement | null;

  export const IconButton: React.FunctionComponent<
    {
      align?:
        | 'top'
        | 'top-left'
        | 'top-right'
        | 'bottom'
        | 'bottom-left'
        | 'bottom-right'
        | 'left'
        | 'right';
      defaultOpen?: boolean;
      disabled?: boolean;
      enterDelayMs?: number;
      kind?: 'primary' | 'secondary' | 'ghost' | 'tertiary';
      label: string;
      leaveDelayMs?: number;
      size?: 'sm' | 'md' | 'lg';
      className?: string;
      onClick?: () => void;
      children?: React.ReactNode;
    } & React.ButtonHTMLAttributes<HTMLButtonElement>
  >;

  export const ProgressBar: React.FunctionComponent<{
    className?: string;
    helperText?: string;
    hideLabel?: bool;
    label: string;
    max?: number;
    size?: 'small' | 'big';
    status?: 'active' | 'finished' | 'error';
    type?: 'default' | 'inline' | 'indented';
    value?: number;
  }>;

  export const useTheme: any;
  export const ActionableNotification: React.FunctionComponent<{
    actionButtonLabel: string;
    ['aria-label']?: string;
    caption?: string;
    children?: node;
    className?: string;
    closeOnEscape?: boolean;
    hasFocus?: boolean;
    hideCloseButton?: boolean;
    inline?: boolean;
    kind:
      | 'error'
      | 'info'
      | 'info-square'
      | 'success'
      | 'warning'
      | 'warning-alt';

    lowContrast?: boolean;
    onActionButtonClick?: func;
    onClose?: func;
    onCloseButtonClick?: func;
    role?: string;
    statusIconDescription?: string;
    subtitle?: string;
    title?: string;
  }>;

  export const Checkbox: React.FunctionComponent<
    Omit<ReactInputAttr, ExcludedAttributes> & {
      hideLabel?: boolean | undefined;
      id: string;
      labelText: NonNullable<React.ReactNode>;
      checked?: boolean;
      defaultChecked?: boolean | undefined;
      disabled?: boolean;
      helperText?: React.ReactNode;
      hideLabel?: boolean;
      indeterminate?: boolean;
      invalid?: boolean;
      invalidText?: React.ReactNode;
      warn?: boolean;
      warnText?: React.ReactNode;
      onChange?: (
        evt: React.ChangeEvent<HTMLInputElement>,
        data: {checked: boolean; id: string},
      ) => void;
      onClick?: (evt: React.MouseEvent<HTMLInputElement>) => void;
      autoFocus?: boolean;
    }
  >;

  export const OverflowMenu: ForwardRefReturn<
    HTMLButtonElement,
    React.FunctionComponent<
      Omit<ReactInputAttr, ExcludedAttributes> & {
        ariaLabel?: string | undefined;
        direction?: VerticalDirection | undefined;
        iconClass?: ReactAttr['className'] | undefined;
        iconDescription?: string | undefined;
        flipped?: boolean | undefined;
        focusTrap?: boolean | undefined;
        /**
         * @deprecated The `light` prop for `OverflowMenu` is no longer needed and has been deprecated. It will be removed in the next major release. Use the Layer component instead.
         */
        light?: boolean | undefined;
        menuOffset?: MenuOffsetValue | undefined;
        menuOffsetFlip?: MenuOffsetValue | undefined;
        menuOptionsClass?: ReactAttr['className'] | undefined;
        onClick?(
          event:
            | React.MouseEvent<HTMLElement>
            | React.KeyboardEvent<HTMLElement>,
        ): void;
        onClose?(): void;
        onOpen?(): void;
        open?: boolean | undefined;
        renderIcon?: any;
        selectorPrimaryFocus?: string | undefined;
        size?: MenuProps['size'];
      } & {
        align?:
          | 'top'
          | 'top-left'
          | 'top-right'
          | 'bottom'
          | 'bottom-left'
          | 'bottom-right'
          | 'left'
          | 'right';
      }
    >
  >;

  export const TreeView: React.FunctionComponent<{
    active?: string | number;
    children?: React.ReactNode;
    className?: string;
    hideLabel?: boolean;
    label: string;
    multiselect?: boolean;
    onSelect?: PropTypes.func;
    selected?: string[] | number[];
    size?: 'xs' | 'sm';
  }>;

  export const TreeNode: React.FunctionComponent<{
    id?: string;
    active?: string | number;
    children?: React.ReactNode;
    className?: string;
    depth?: number;
    disabled?: boolean;
    isExpanded?: boolean;
    label?: React.ReactNode;
    onSelect?: () => void;
    onToggle?: (
      event: React.MouseEvent<HTMLElement> | React.KeyboardEvent<HTMLElement>,
    ) => void;
    renderIcon?: () => React.ReactNode;
    selected?: string[] | number[];
    value?: string;
    tabIndex?: number;
  }>;

  export const TabList: React.FunctionComponent<{
    activation?: 'automatic' | 'manual';
    'aria-label': string;
    children?: React.ReactNode;
    className?: string;
    contained?: boolean;
    iconSize?: 'default' | 'lg';
    leftOverflowButtonProps?: object;
    rightOverflowButtonProps?: object;
    scrollDebounceWait?: number;
    scrollIntoView?: boolean;
  }>;

  export const TabPanels: React.FunctionComponent<{
    children?: React.ReactNode;
  }>;

  export const TabPanel: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
  }>;

  export const Heading: React.ForwardRefExoticComponent<
    Omit<
      React.DetailedHTMLProps<
        React.HTMLAttributes<HTMLHeadingElement>,
        HTMLHeadingElement
      >,
      'ref'
    > &
      React.RefAttributes<HTMLHeadingElement>
  >;

  export * from 'carbon-components-react';
}

declare module '@carbon/elements' {
  import {styles as originalStyles} from '@types/carbon__elements';
  export const styles = {
    ...originalStyles,
    legal01: {
      fontSize: '0.75rem',
      fontWeight: 400,
      lineHeight: 1.33333,
      letterSpacing: '0.32px',
    },
    legal02: {
      fontSize: '0.875rem',
      fontWeight: 400,
      lineHeight: 1.28572,
      letterSpacing: '0.16px',
    },
  } as const;
  export * from '@types/carbon__elements';
}

declare module '@carbon/react/icons' {
  type Icon = React.FunctionComponent<
    {
      className?: string;
      alt?: string;
      'aria-label'?: string;
      size?: 16 | 20 | 24 | 32;
    } & React.HTMLAttributes<HTMLOrSVGElement>
  >;

  export const ArrowRight: Icon;
  export const RowExpand: Icon;
  export const RowCollapse: Icon;
  export const CheckmarkOutline: Icon;
  export const WarningFilled: Icon;
  export const TrashCan: Icon;
  export const Error: Icon;
  export const Tools: Icon;
  export const RetryFailed: Icon;
  export const Edit: Icon;
  export const Filter: Icon;
  export const RadioButtonChecked: Icon;
  export const Calendar: Icon;
  export const Add: Icon;
  export const Close: Icon;
  export const Popup: Icon;
  export const WarningAltFilled: Icon;
  export const Subtract: Icon;
  export const CenterCircle: Icon;
  export const Checkmark: Icon;
  export const Maximize: Icon;
  export const Minimize: Icon;
  export const MigrateAlt: Icon;
  export const Move: Icon;
  export const Copy: Icon;
  export const Launch: Icon;
}
