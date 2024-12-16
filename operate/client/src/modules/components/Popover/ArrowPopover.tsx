/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Side,
  useFloating,
  arrow,
  Placement,
  autoUpdate,
  Middleware,
  shift,
} from '@floating-ui/react-dom';

import {useEffect, useLayoutEffect, useRef, useState} from 'react';
import {Container, Arrow, getArrowPosition} from './styled';
import isNil from 'lodash/isNil';
import {createPortal} from 'react-dom';

function getSide(placement: Placement) {
  const [side] = placement.split('-');

  return side as Side;
}

function getValueWhenValidNumber(value: number | undefined | null) {
  if (isNil(value) || isNaN(value)) {
    return 0;
  }

  return value;
}

type Props = {
  referenceElement?: Element | null;
  children: React.ReactNode;
  placement?: Placement;
  className?: string;
  onOutsideClick?: (event: MouseEvent) => void;
  middlewareOptions?: Middleware[];
  autoUpdatePosition?: boolean;
};

const APP_HEADER_HEIGHT = 47;

const ArrowPopover: React.FC<Props> = ({
  referenceElement,
  children,
  placement = 'bottom',
  className,
  onOutsideClick,
  middlewareOptions = [],
  autoUpdatePosition = false,
}) => {
  const arrowElementRef = useRef<HTMLDivElement | null>(null);
  const {
    x,
    y,
    strategy,
    middlewareData,
    placement: actualPlacement,
    refs: {floating, setFloating},
  } = useFloating({
    placement,
    middleware: [
      ...middlewareOptions,
      shift({padding: {top: APP_HEADER_HEIGHT}}),
      arrow({element: arrowElementRef}),
    ],
    whileElementsMounted: autoUpdatePosition ? autoUpdate : undefined,
    elements: {
      reference: referenceElement,
    },
  });

  const [isHidden, setIsHidden] = useState<boolean>(false);

  useEffect(() => {
    if (middlewareData.hide && floating?.current !== null) {
      setIsHidden(!!middlewareData.hide.referenceHidden);
    }
  }, [floating, middlewareData]);

  useLayoutEffect(() => {
    const handleClick = (event: MouseEvent) => {
      const target = event.target;

      if (target instanceof Element && !floating.current?.contains(target)) {
        onOutsideClick?.(event);
      }
    };

    document.body?.addEventListener('click', handleClick, true);
    return () => {
      document.body?.removeEventListener('click', handleClick, true);
    };
  }, [onOutsideClick, floating]);

  const {x: arrowX, y: arrowY} = middlewareData.arrow ?? {};

  return referenceElement === null
    ? null
    : createPortal(
        <Container
          className={className}
          ref={setFloating}
          style={{
            position: strategy,
            top: getValueWhenValidNumber(y),
            left: getValueWhenValidNumber(x),
          }}
          data-testid="popover"
        >
          {!isHidden && (
            <>
              <Arrow
                ref={arrowElementRef}
                style={{
                  ...getArrowPosition({
                    side: getSide(actualPlacement),
                    x: getValueWhenValidNumber(arrowX),
                    y: getValueWhenValidNumber(arrowY),
                  }),
                }}
              />
              <div>{children}</div>
            </>
          )}
        </Container>,
        document.body,
      );
};

export {ArrowPopover};
