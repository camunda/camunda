/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Side,
  useFloating,
  offset,
  flip,
  arrow,
  Placement,
} from '@floating-ui/react-dom';
import {createPortal} from 'react-dom';
import {useLayoutEffect, useRef} from 'react';
import {Container, Arrow, getArrowPosition} from './styled';
import {isNil} from 'lodash';

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
  flipOptions?: Parameters<typeof flip>;
  offsetOptions?: Parameters<typeof offset>;
  className?: string;
  onOutsideClick?: (event: MouseEvent) => void;
};

const Popover: React.FC<Props> = ({
  referenceElement,
  children,
  placement = 'bottom',
  flipOptions = [],
  offsetOptions = [],
  className,
  onOutsideClick,
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
    middleware: [
      offset(...offsetOptions),
      flip(...flipOptions),
      arrow({element: arrowElementRef}),
    ],
  });

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

export {Popover};
