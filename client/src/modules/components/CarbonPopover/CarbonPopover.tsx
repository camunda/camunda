/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode, useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react';
import {Button, Icon, Tooltip} from 'components';
import {getScreenBounds} from 'services';
import {Popover, PopoverAlignment, PopoverContent, PopoverProps} from '@carbon/react';

import classNames from 'classnames';

import './CarbonPopover.scss';

const possibleAlignments: PopoverAlignment[] = [
  'top',
  'top-left',
  'top-right',
  'bottom',
  'bottom-left',
  'bottom-right',
  'left',
  'left-bottom',
  'left-top',
  'right',
  'right-bottom',
  'right-top',
];

interface CarbonPopoverProps extends Omit<PopoverProps<'div'>, 'title' | 'open' | 'align'> {
  className?: string;
  children: ReactNode;
  title?: ReactNode;
  main?: boolean;
  disabled?: boolean;
  icon?: string;
  floating?: boolean;
  onOpen?: () => void;
  onClose?: () => void;
  tooltip?: ReactNode;
  autoOpen?: boolean;
  align?: (typeof possibleAlignments)[number];
}

export default function CarbonPopover({
  className,
  children,
  title,
  main,
  disabled,
  icon,
  floating,
  onOpen,
  onClose,
  tooltip,
  autoOpen = false,
  align,
  ...props
}: CarbonPopoverProps): JSX.Element {
  const [open, setOpen] = useState(autoOpen);
  const [scrollable, setScrollable] = useState<boolean>(false);
  const [popoverStyles, setPopoverStyles] = useState({});
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const isInsideClick = useRef<boolean>(false);

  const calculateDialogStyle = useCallback(() => {
    const popoverClassList = popoverRef.current?.classList;
    if (!popoverClassList || !buttonRef.current || !dialogRef.current || !contentRef.current) {
      return;
    }

    const dialogStyles = dialogRef.current.style;

    popoverClassList.remove(...possibleAlignments.map(getClassName));
    dialogRef.current.removeAttribute('style');

    const overlayWidth = dialogRef.current.clientWidth;
    const overlayHeight = dialogRef.current.clientHeight;
    const contentHeight = contentRef.current.clientHeight;
    const buttonRect = buttonRef.current.getBoundingClientRect();
    const buttonCenter = buttonRect.left + buttonRect.width / 2;
    const bounds = getModalBounds(dialogRef.current) || getScreenBounds();

    const bodyWidth = document.body.clientWidth;
    const margin = 10;
    const padding = 10 + 15;

    let newAlignment = 'bottom';

    if (buttonCenter + overlayWidth / 2 > bodyWidth) {
      newAlignment = 'bottom-right';
    }

    if (buttonCenter - overlayWidth / 2 < 0) {
      newAlignment = 'bottom-left';
    }

    if (
      overlayHeight + buttonRect.bottom > bounds.bottom - margin ||
      contentHeight > overlayHeight
    ) {
      dialogStyles.height = bounds.bottom - buttonRect.bottom - 2 * margin + 'px';
      setScrollable(true);
    }

    const topSpace = buttonRect.bottom - bounds.top - margin;
    const bottomSpace = bounds.bottom - buttonRect.bottom - margin;
    const contentHeightWithPadding = contentHeight + padding;

    if (bottomSpace < contentHeightWithPadding && topSpace > bottomSpace) {
      const scrollable = contentHeightWithPadding > topSpace;
      setScrollable(scrollable);
      dialogStyles.height = (scrollable ? topSpace : contentHeightWithPadding) + 'px';
      newAlignment = newAlignment.replace('bottom', 'top');
    }

    popoverClassList.add(getClassName(align || newAlignment));
  }, [align]);

  const fixPositioning = useCallback(() => {
    if (!floating) {
      return;
    }

    const overlay = popoverRef.current?.querySelector('.cds--popover');
    const modalBounds = getModalBounds(popoverRef.current);
    const modalTop = modalBounds?.top || 0;
    const modalLeft = modalBounds?.left || 0;

    const box = buttonRef.current?.getBoundingClientRect();
    if (open && overlay && box) {
      setPopoverStyles({
        position: 'fixed',
        left: box.left - modalLeft + 'px',
        top: box.top - modalTop + 'px',
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
  }, [handleResize, open]);

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

  return (
    <Popover
      className={classNames(className, 'CarbonPopover')}
      {...props}
      open={open}
      ref={popoverRef}
    >
      <Tooltip content={tooltip}>
        <div className="buttonWrapper">
          <Button
            onClick={() => setOpen(!open)}
            active={!disabled && open}
            main={main}
            disabled={disabled}
            icon={!!icon && !title}
            ref={buttonRef}
          >
            {icon ? <Icon type={icon} /> : ''}
            {title}
            <Icon type="down" className="downIcon" />
          </Button>
        </div>
      </Tooltip>

      {open && (
        <PopoverContent
          className={classNames('popoverContent', {scrollable})}
          ref={dialogRef}
          style={popoverStyles}
          onMouseDownCapture={() => {
            isInsideClick.current = true;
          }}
        >
          <div ref={contentRef}>{children}</div>
        </PopoverContent>
      )}
    </Popover>
  );
}

function getModalBounds(el: Element | null) {
  return el?.closest('.cds--modal.is-visible .cds--modal-container')?.getBoundingClientRect();
}

function getClassName(alignment: string) {
  return 'cds--popover--' + alignment;
}
