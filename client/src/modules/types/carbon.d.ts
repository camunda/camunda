/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  type ThemeType = 'white' | 'g10' | 'g90' | 'g100';

  export const Theme: React.FunctionComponent<{
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
    props: StackProps<C>,
  ) => React.ReactElement | null;

  export const ActionableNotification: React.FunctionComponent<{
    actionButtonLabel: string;
    ariaLabel?: string;
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
  }>;

  export const useTheme: () => {theme: ThemeType};

  export const usePrefix: () => string;

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
