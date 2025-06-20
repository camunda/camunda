/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
