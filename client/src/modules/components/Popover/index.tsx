/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {usePopper} from 'react-popper';
import {createPortal} from 'react-dom';
import {useLayoutEffect, useRef, useState} from 'react';
import {Container, Arrow} from './styled';
import offsetModifier from '@popperjs/core/lib/modifiers/offset';
import flipModifier from '@popperjs/core/lib/modifiers/flip';
import {Placement} from '@popperjs/core';

type Props = {
  referenceElement?: Element | null;
  children: React.ReactNode;
  placement?: Placement;
  flipOptions?: typeof flipModifier['options'];
  offsetOptions?: typeof offsetModifier['options'];
  className?: string;
  onOutsideClick?: () => void;
};

const Popover: React.FC<Props> = ({
  referenceElement,
  children,
  placement = 'bottom',
  flipOptions = {},
  offsetOptions = {},
  className,
  onOutsideClick,
}) => {
  const popoverElementRef = useRef<HTMLDivElement | null>(null);
  const [arrow, setArrow] = useState<HTMLElement | null>(null);

  useLayoutEffect(() => {
    const handleClick = (event: MouseEvent) => {
      const target = event.target;

      if (
        target instanceof Element &&
        !popoverElementRef.current?.contains(target)
      ) {
        onOutsideClick?.();
      }
    };

    document.body?.addEventListener('click', handleClick);
    return () => {
      document.body?.removeEventListener('click', handleClick);
    };
  }, [onOutsideClick]);

  const {styles, attributes} = usePopper(
    referenceElement,
    popoverElementRef.current,
    {
      placement,
      modifiers: [
        {
          name: 'offset',
          options: offsetOptions,
        },
        {
          name: 'flip',
          options: flipOptions,
        },
        {
          name: 'arrow',
          options: {
            element: arrow,
          },
        },
      ],
    }
  );

  return referenceElement !== null
    ? createPortal(
        <Container
          ref={popoverElementRef}
          style={styles.popper}
          className={className}
          {...attributes.popper}
        >
          <Arrow ref={setArrow} style={styles.arrow} {...attributes.arrow} />
          <div data-testid="popover">{children}</div>
        </Container>,
        document.body
      )
    : null;
};

export {Popover};
