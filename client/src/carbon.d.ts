/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps, ReactNode, RefObject} from 'react';

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
}
