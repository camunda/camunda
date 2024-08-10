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
  Props = object,
> = React.PropsWithChildren<Props & AsProp<C>> &
  Omit<React.ComponentPropsWithoutRef<C>, PropsToOmit<C, Props>>;

type PolymorphicComponentPropWithRef<
  C extends React.ElementType,
  Props = object,
> = PolymorphicComponentProp<C, Props> & {ref?: PolymorphicRef<C>};

type Size = 'sm' | 'md' | 'lg' | 'xl';

declare module '@carbon/react' {
  type ThemeType = 'white' | 'g10' | 'g90' | 'g100';

  export const Theme: React.FunctionComponent<{
    children: React.ReactNode;
    theme?: ThemeType;
    className?: string;
  }>;

  export const GlobalTheme: React.FunctionComponent<{
    children: React.ReactNode;
    theme?: ThemeType;
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

  export const Header: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
    'aria-label'?: string;
    'aria-labelledby'?: string;
  }>;

  export const Toggletip: React.FunctionComponent<{
    align?: 'top' | 'bottom' | 'left' | 'right';
    as?: keyof JSX.IntrinsicElements | React.ReactElement;
    children?: React.ReactNode;
    className?: string;
    defaultOpen?: boolean;
  }>;

  export const ToggletipButton: React.FunctionComponent<{
    children: React.ReactNode;
    className?: string;
    label?: string;
  }>;

  export const ToggletipContent: React.FunctionComponent<{
    children: React.ReactNode;
    className?: string;
  }>;

  export const SwitcherItem: React.FunctionComponent<
    {
      children: React.ReactNode;
    } & React.AnchorHTMLAttributes<HTMLAnchorElement>
  >;

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

  export const ActionableNotification: React.FunctionComponent<{
    actionButtonLabel: string;
    'aria-label'?: string;
    caption?: string;
    children?: React.ReactNode;
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
    onActionButtonClick?: () => void;
    onClose?: () => void;
    onCloseButtonClick?: () => void;
    role?: string;
    statusIconDescription?: string;
    subtitle?: string;
    title?: string;
    style?: Partial<CSSStyleDeclaration>;
  }>;

  export const ContainedList: React.FunctionComponent<{
    action?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    isInset?: boolean;
    kind?: 'on-page' | 'disclosed';
    label: string | React.ReactNode;
    size?: Size;
  }>;

  export const ContainedListItem: React.FunctionComponent<{
    action?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    disabled?: boolean;
    onClick?: () => void;
    renderIcon?: (() => void) | object;
  }>;

  type SectionProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        children?: React.ReactNode;
        className?: string;
        level?: 1 | 2 | 3 | 4 | 5 | 6;
      }
    >;

  export const Section: <C extends React.ElementType = 'section'>(
    props: SectionProps<C>,
  ) => React.ReactElement | null;

  export const Heading: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
  }>;

  export const Toggle: React.FC<{
    'aria-labelledby'?: string;
    id: string;
    className?: string;
    labelA?: string;
    labelB?: string;
    labelText?: string;
    hideLabel?: boolean;
    onClick?:
      | React.MouseEventHandler<HTMLDivElement>
      | React.KeyboardEventHandler<HTMLDivElement>;
    onToggle?(checked: boolean): void;
    size?: 'sm' | 'md';
    readOnly?: boolean;
    defaultToggled?: boolean;
    toggled?: boolean;
  }>;

  export const Copy: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
    align?:
      | 'top'
      | 'top-left'
      | 'top-right'
      | 'bottom'
      | 'bottom-left'
      | 'bottom-right'
      | 'left'
      | 'right';
    feedback?: string;
    feedbackTimeout?: number;
    onAnimationEnd?: () => void;
    onClick?: () => void;
  }>;

  type FlexGridProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        className?: string;
        condensed?: boolean;
        fullWidth?: boolean;
        narrow?: boolean;
      }
    >;

  export const FlexGrid: <C extends React.ElementType = 'div'>(
    props: FlexGridProps<C>,
  ) => React.ReactElement | null;

  export const useTheme: () => {theme: ThemeType};

  export const usePrefix: () => string;

  export const OverflowMenu: React.FunctionComponent<{
    'aria-label'?: string;
    children?: React.ReactNode;
    className?: string;
    direction?: 'top' | 'bottom';
    flipped?: boolean;
    focusTrap?: boolean;
    iconClass?: string;
    iconDescription?: string;
    id?: string;
    menuOffset?: {top: number; left: number} | (() => void);
    menuOffsetFlip?: {top: number; left: number} | (() => void);
    menuOptionsClass?: string;
    onClick?: React.MouseEventHandler<HTMLDivElement>;
    onClose?: () => void;
    onFocus?: React.FocusEventHandler<HTMLDivElement>;
    onKeyDown?: React.KeyboardEventHandler<HTMLDivElement>;
    onBlur?: React.FocusEventHandler<HTMLDivElement>;
    disabled?: boolean;
    onOpen?: () => void;
    open?: boolean;
    renderIcon?: React.FC | object;
    selectorPrimaryFocus?: string;
    size?: 'sm' | 'md' | 'lg';
    align?:
      | 'top'
      | 'top-left'
      | 'top-right'
      | 'bottom'
      | 'bottom-left'
      | 'bottom-right'
      | 'left'
      | 'right';
  }>;

  interface PopoverContext {
    setFloating: React.Ref<HTMLSpanElement>;
    caretRef: React.Ref<HTMLSpanElement>;
    autoAlign: boolean | null;
  }

  type PopoverAlignment =
    | 'top'
    | 'top-left' // deprecated
    | 'top-right' // deprecated
    | 'bottom'
    | 'bottom-left' // deprecated
    | 'bottom-right' // deprecated
    | 'left'
    | 'left-bottom' // deprecated
    | 'left-top' // deprecated
    | 'right'
    | 'right-bottom' // deprecated
    | 'right-top' // deprecated
    // new values to match floating-ui
    | 'top-start'
    | 'top-end'
    | 'bottom-start'
    | 'bottom-end'
    | 'left-end'
    | 'left-start'
    | 'right-end'
    | 'right-start';

  interface PopoverBaseProps {
    align?: PopoverAlignment;
    autoAlign?: boolean;
    caret?: boolean;
    children?: React.ReactNode;
    className?: string;
    dropShadow?: boolean;
    highContrast?: boolean;
    isTabTip?: boolean;
    onRequestClose?: () => void;
    open: boolean;
  }

  type PopoverProps<E extends ElementType> = PolymorphicProps<
    E,
    PopoverBaseProps
  >;

  export const Popover = React.FC<PopoverProps<'span'>>;

  export const PopoverContent = React.FC<React.HTMLAttributes<HTMLSpanElement>>;

  export interface TabsProps {
    /**
     * Provide child elements to be rendered inside the `Tabs`.
     * These elements should render either `TabsList` or `TabsPanels`
     */
    children?: ReactNode;

    /**
     * Specify which content tab should be initially selected when the component
     * is first rendered
     */
    defaultSelectedIndex?: number;

    /**
     * Whether the rendered Tab children should be dismissable.
     */
    dismissable?: boolean;

    /**
     * Provide an optional function which is called
     * whenever the state of the `Tabs` changes
     */
    onChange?(state: {selectedIndex: number}): void;

    /**
     * If specifying the `onTabCloseRequest` prop, provide a callback function
     * responsible for removing the tab when close button is pressed on one of the Tab elements
     */
    onTabCloseRequest?(tabIndex: number): void;

    /**
     * Control which content panel is currently selected. This puts the component
     * in a controlled mode and should be used along with `onChange`
     */
    selectedIndex?: number;
  }

  export const Tabs: React.FunctionComponent<TabsProps>;

  export interface TabListProps extends DivAttributes {
    /**
     * Specify whether the content tab should be activated automatically or
     * manually
     */
    activation?: 'automatic' | 'manual';

    /**
     * Provide an accessible label to be read when a user interacts with this
     * component
     */
    'aria-label': string;

    /**
     * Provide child elements to be rendered inside `ContentTabs`.
     * These elements should render a `ContentTab`
     */
    children?: ReactNode;

    /**
     * Specify an optional className to be added to the container node
     */
    className?: string;

    /**
     * Specify whether component is contained type
     */
    contained?: boolean;

    /**
     * Used for tabs within a grid, this makes it so tabs span the full container width and have the same width. Only available on contained tabs with <9 children
     */
    fullWidth?: boolean;

    /**
     * If using `IconTab`, specify the size of the icon being used.
     */
    iconSize?: 'default' | 'lg';

    /**
     * Provide the props that describe the left overflow button
     */
    leftOverflowButtonProps?: HTMLAttributes<HTMLButtonElement>;

    /**
     * Specify whether to use the light component variant
     */
    light?: boolean;

    /**
     * Provide the props that describe the right overflow button
     */
    rightOverflowButtonProps?: HTMLAttributes<HTMLButtonElement>;

    /**
     * Optionally provide a delay (in milliseconds) passed to the lodash
     * debounce of the onScroll handler. This will impact the responsiveness
     * of scroll arrow buttons rendering when scrolling to the first or last tab.
     */
    scrollDebounceWait?: number;

    /**
     * Choose whether to automatically scroll to newly selected tabs
     * on component rerender
     */
    scrollIntoView?: boolean;
  }

  export const TabList: React.FunctionComponent<TabListProps>;

  export * from 'carbon-components-react';
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
  export const Devices: Icon;
  export const Moon: Icon;
  export const Light: Icon;
  export const InformationFilled: Icon;
  export const Information: Icon;
  export const Search: Icon;
  export const Close: Icon;
  export const Popup: Icon;
  export const Add: Icon;
  export const RowCollapse: Icon;
  export const SortAscending: Icon;
  export const Checkmark: Icon;
  export const Error: Icon;
  export const Warning: Icon;
  export const WarningFilled: Icon;
  export const CheckmarkOutline: Icon;
  export const RadioButtonChecked: Icon;
  export const Share: Icon;
  export const Filter: Icon;
  export const CircleDash: Icon;
  export const UserAvatar: Icon;
  export const UserAvatarFilled: Icon;
  export const Calendar: Icon;
  export const CheckmarkFilled: Icon;
  export const Notification: Icon;
  export const CenterCircle: Icon;
  export const Subtract: Icon;
  export const SidePanelOpen: Icon;
  export const SidePanelClose: Icon;
}

declare module '@carbon/elements' {
  import {
    white,
    whiteHover,
    gray10,
    gray10Hover,
    gray20,
    gray20Hover,
    gray30,
    gray60,
    gray70,
    gray70Hover,
    gray80,
    gray80Hover,
    gray90Hover,
    styles as originalStyles,
    g10 as originalG10,
    g100 as originalG100,
  } from '@types/carbon__elements';

  const borderSubtle00: typeof gray10 | typeof gray80;
  const borderSubtle01: typeof gray20 | typeof gray80;
  const layer01: typeof gray70 | typeof white;
  const layerActive01: typeof gray30 | typeof gray70;
  const borderSubtleSelected01: typeof gray30 | typeof gray70;
  const layerHover01: typeof whiteHover | typeof gray90Hover;
  const layerSelected01: typeof gray20 | typeof gray80;
  const layerSelectedHover01: typeof gray20Hover | typeof gray80Hover;
  const layer02: typeof gray10 | typeof gray80;
  const layerActive02: typeof gray30 | typeof gray60;
  const layerHover02: typeof gray10Hover | typeof gray80Hover;
  const layerSelected02: typeof gray20 | typeof gray70;
  const layerSelectedHover02: typeof gray20Hover | typeof gray70Hover;
  const g10 = {
    ...originalG10,
    borderSubtle00,
    borderSubtle01,
    layer01,
    layerActive01,
    borderSubtleSelected01,
    layerHover01,
    layerSelected01,
    layerSelectedHover01,
    layer02,
    layerActive02,
    layerHover02,
    layerSelected02,
    layerSelectedHover02,
  } as const;
  const g100 = {
    ...originalG100,
    borderSubtle00,
    borderSubtle01,
    layer01,
    layerActive01,
    borderSubtleSelected01,
    layerHover01,
    layerSelected01,
    layerSelectedHover01,
    layer02,
    layerActive02,
    layerHover02,
    layerSelected02,
    layerSelectedHover02,
  } as const;
  const styles = {
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

  export {borderSubtle00, borderSubtle01, styles, g10, g100};
}
