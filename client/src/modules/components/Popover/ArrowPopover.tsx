/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Side,
  useFloating,
  arrow,
  Placement,
  autoUpdate,
  Middleware,
} from '@floating-ui/react-dom';

import {useEffect, useLayoutEffect, useRef} from 'react';
import {Container, Arrow, getArrowPosition} from './styled';
import {isNil} from 'lodash';
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
    floating,
    x,
    y,
    strategy,
    reference,
    middlewareData,
    placement: actualPlacement,
    refs: {floating: popoverElementRef},
  } = useFloating({
    placement,
    middleware: [...middlewareOptions, arrow({element: arrowElementRef})],
    whileElementsMounted: autoUpdatePosition ? autoUpdate : undefined,
  });

  useEffect(() => {
    if (middlewareData.hide && popoverElementRef?.current !== null) {
      Object.assign(popoverElementRef.current.style, {
        display: middlewareData.hide.referenceHidden ? 'none' : 'block',
      });
    }
  }, [popoverElementRef, middlewareData]);

  useLayoutEffect(() => {
    const handleClick = (event: MouseEvent) => {
      const target = event.target;

      if (
        target instanceof Element &&
        !popoverElementRef.current?.contains(target)
      ) {
        onOutsideClick?.(event);
      }
    };

    document.body?.addEventListener('click', handleClick, true);
    return () => {
      document.body?.removeEventListener('click', handleClick, true);
    };
  }, [onOutsideClick, popoverElementRef]);

  useLayoutEffect(() => {
    if (referenceElement) {
      reference(referenceElement);
    }
  }, [referenceElement, reference]);

  const {x: arrowX, y: arrowY} = middlewareData.arrow ?? {};

  return referenceElement === null
    ? null
    : createPortal(
        <Container
          className={className}
          ref={floating}
          style={{
            position: strategy,
            top: getValueWhenValidNumber(y),
            left: getValueWhenValidNumber(x),
          }}
          data-testid="popover"
        >
          <Arrow
            ref={arrowElementRef}
            style={{
              ...getArrowPosition({
                side: getSide(actualPlacement),
                x: getValueWhenValidNumber(arrowX),
                y: getValueWhenValidNumber(arrowY),
              }),
            }}
            $side={getSide(actualPlacement)}
          />
          <div>{children}</div>
        </Container>,
        document.body
      );
};

export {ArrowPopover};
