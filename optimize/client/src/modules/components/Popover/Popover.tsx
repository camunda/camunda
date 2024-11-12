/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ComponentProps,
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';
import {
  Popover as CarbonPopover,
  PopoverAlignment,
  PopoverContent,
  PopoverProps as CarbonPopoverProps,
  Button,
  Layer,
} from '@carbon/react';
import ListBox from '@carbon/react/lib/components/ListBox';

import {getRandomId, getScreenBounds} from 'services';

import classNames from 'classnames';

import './Popover.scss';

interface PopoverProps extends Omit<CarbonPopoverProps<'div'>, 'open'> {
  className?: string;
  children: ReactNode;
  floating?: boolean;
  onOpen?: () => void;
  onClose?: () => void;
  autoOpen?: boolean;
  trigger: NonNullable<ReactNode>;
}

interface TriggerContextProps {
  open: boolean;
  setOpen: React.Dispatch<React.SetStateAction<boolean>>;
  buttonRef?: React.MutableRefObject<HTMLButtonElement | null>;
  popoverId?: string;
}

const TriggerContext = createContext<TriggerContextProps>({open: false, setOpen: () => {}});

export default function Popover({
  className,
  children,
  floating,
  onOpen,
  onClose,
  autoOpen = false,
  align,
  trigger,
  ...props
}: PopoverProps): JSX.Element {
  const [open, setOpen] = useState(autoOpen);
  const [scrollable, setScrollable] = useState<boolean>(false);
  const [popoverStyles, setPopoverStyles] = useState({});
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const parentContainer = useRef<HTMLElement | null>(null);
  const isInsideClick = useRef<boolean>(false);
  const [alignment, setAlignment] = useState<PopoverAlignment | undefined>(align);

  const calculateDialogStyle = useCallback(() => {
    if (!buttonRef.current || !dialogRef.current || !contentRef.current) {
      return;
    }

    setScrollable(false);

    const dialogStyles = dialogRef.current.style;

    dialogRef.current.removeAttribute('style');

    const overlayWidth = dialogRef.current.clientWidth;
    const overlayHeight = dialogRef.current.clientHeight;
    const contentHeight = contentRef.current.clientHeight;
    const buttonRect = buttonRef.current.getBoundingClientRect();
    const buttonCenter = buttonRect.left + buttonRect.width / 2;

    const bounds = getScrollBounds(parentContainer.current);

    const bodyWidth = document.body.clientWidth;
    const margin = 10;
    const padding = 10 + 15;
    const caretSpacing = props.isTabTip ? 0 : 10;

    let newAlignment: PopoverAlignment = align || props.isTabTip ? 'bottom-left' : 'bottom';

    // if the button is centered we are using half of its width to calculate the position, if its sticking to the left or right we are using the full width
    const divisor = newAlignment.includes('left') || newAlignment.includes('right') ? 1 : 2;

    if (buttonCenter + overlayWidth / divisor > bodyWidth) {
      newAlignment = 'bottom-right';
    }

    if (buttonCenter - overlayWidth / divisor < 0) {
      newAlignment = 'bottom-left';
    }

    const bottomSpace = bounds.bottom - buttonRect.bottom - margin - caretSpacing;
    const topSpace = buttonRect.top - bounds.top - margin - caretSpacing;

    if (
      overlayHeight + buttonRect.bottom > bounds.bottom - margin ||
      contentHeight > overlayHeight
    ) {
      dialogStyles.height = bottomSpace + 'px';
      setScrollable(true);
    }

    const contentHeightWithPadding = contentHeight + padding;

    if (bottomSpace < contentHeightWithPadding && topSpace > bottomSpace) {
      const scrollable = contentHeightWithPadding > topSpace;
      setScrollable(scrollable);
      dialogStyles.height = (scrollable ? topSpace : contentHeightWithPadding) + 'px';
      newAlignment = newAlignment.replace('bottom', 'top') as PopoverAlignment;
    }

    setAlignment(newAlignment);
  }, [align, props.isTabTip]);

  const fixPositioning = useCallback(() => {
    if (!floating) {
      return;
    }

    const {top, left} = parentContainer.current?.getBoundingClientRect() || {
      top: 0,
      left: 0,
    };
    const box = buttonRef.current?.getBoundingClientRect();

    if (open && box) {
      setPopoverStyles({
        position: 'fixed',
        left: box.left - left + 'px',
        top: box.top - top + 'px',
        width: box.width,
        height: box.height,
      });
    }
  }, [floating, open]);

  useEffect(() => {
    if (open) {
      onOpen?.();
    } else if (!open) {
      onClose?.();
    }
  }, [onClose, onOpen, open]);

  const handleResize = useCallback(() => {
    calculateDialogStyle();
    fixPositioning();
  }, [calculateDialogStyle, fixPositioning]);

  useLayoutEffect(() => {
    const observer = new MutationObserver(handleResize);

    if (open) {
      if (floating) {
        parentContainer.current = getClosestElementByStyle(
          popoverRef.current,
          (style) => style.transform !== 'none'
        );
      } else {
        parentContainer.current =
          popoverRef.current?.closest<HTMLElement>('.popoverContent.scrollable') || null;
      }
      handleResize();
      window.addEventListener('resize', handleResize);
      if (dialogRef.current) {
        observer.observe(dialogRef.current, {
          childList: true,
          subtree: true,
        });
      }
    } else {
      window.removeEventListener('resize', handleResize);
      observer.disconnect();
    }

    return () => {
      window.removeEventListener('resize', handleResize);
      observer.disconnect();
    };
  }, [handleResize, open, floating]);

  const handleOutsideClick = (evt: Event) => {
    if (
      popoverRef.current &&
      evt.target instanceof Element &&
      !popoverRef.current.contains(evt.target) &&
      !isInsideClick.current
    ) {
      setOpen(false);
    }

    isInsideClick.current = false;
  };

  useEffect(() => {
    if (open) {
      document.addEventListener('click', handleOutsideClick, {capture: true});
    } else {
      document.removeEventListener('click', handleOutsideClick, {capture: true});
    }

    return () => {
      document.removeEventListener('click', handleOutsideClick, {capture: true});
    };
  }, [open]);

  const popoverId = getRandomId();

  return (
    <TriggerContext.Provider value={{open, setOpen, buttonRef, popoverId}}>
      <CarbonPopover
        className={classNames(className, 'Popover')}
        {...props}
        align={alignment}
        open={open}
        ref={popoverRef}
      >
        {trigger}
        {open && (
          <PopoverContent
            id={popoverId}
            className={classNames('popoverContent', {scrollable})}
            ref={dialogRef}
            style={popoverStyles}
            onMouseDownCapture={() => {
              isInsideClick.current = true;
            }}
          >
            <Layer ref={contentRef}>{children}</Layer>
          </PopoverContent>
        )}
      </CarbonPopover>
    </TriggerContext.Provider>
  );
}

interface ListBoxTriggerProps {
  label: ReactNode;
  children: ReactNode;
  disabled?: boolean;
}

Popover.ListBox = function ListBoxTrigger({label, children, disabled}: ListBoxTriggerProps) {
  const {open, setOpen, buttonRef, popoverId} = useContext(TriggerContext);

  const buttonId = getRandomId();

  return (
    <>
      {label && (
        <label htmlFor={buttonId} className="cds--label">
          {label}
        </label>
      )}
      <ListBox isOpen={open} size="sm" disabled={disabled} className="ListBoxTrigger">
        <button
          id={buttonId}
          type="button"
          ref={buttonRef}
          className="cds--list-box__field"
          disabled={disabled}
          onClick={() => setOpen(!open)}
          aria-haspopup
          aria-expanded={open}
          aria-controls={open ? popoverId : undefined}
        >
          <span className="cds--list-box__label"> {children}</span>
          <ListBox.MenuIcon isOpen={open} />
        </button>
      </ListBox>
    </>
  );
};

type PopoverButtonFixedProps =
  | 'id'
  | 'ref'
  | 'onClick'
  | 'aria-haspopup'
  | 'aria-expanded'
  | 'aria-controls';

Popover.Button = function ButtonTrigger(
  props: Omit<ComponentProps<typeof Button>, PopoverButtonFixedProps>
) {
  const {open, setOpen, buttonRef, popoverId} = useContext(TriggerContext);
  const buttonId = getRandomId();

  return (
    <Button
      kind="ghost"
      {...props}
      id={buttonId}
      ref={buttonRef}
      onClick={() => setOpen(!open)}
      aria-haspopup
      aria-expanded={open}
      aria-controls={open ? popoverId : undefined}
      className={classNames('ButtonTrigger', props.className, {
        active: open,
      })}
    />
  );
};

function getScrollBounds(element: HTMLElement | null) {
  if (!element) {
    return getScreenBounds();
  }

  const scrollParent = getClosestElementByStyle(element, (style) => style.overflow !== 'visible');

  return scrollParent?.getBoundingClientRect() || getScreenBounds();
}

function getClosestElementByStyle(
  element: HTMLElement | null,
  check: (style: CSSStyleDeclaration) => boolean
) {
  let currentNode = element;
  while (currentNode) {
    const computedStyle = window.getComputedStyle(currentNode);
    if (check(computedStyle)) {
      return currentNode;
    }
    currentNode = currentNode.parentElement;
  }

  return null;
}
