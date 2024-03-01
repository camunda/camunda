/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {
  Side,
  useFloating,
  arrow,
  Placement,
  autoUpdate,
  Middleware,
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
    middleware: [...middlewareOptions, arrow({element: arrowElementRef})],
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
