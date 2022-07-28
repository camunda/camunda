/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {usePopper} from 'react-popper';
import {createPortal} from 'react-dom';
import {useRef, useState} from 'react';
import {Container, Arrow} from './styled';
import offsetModifier from '@popperjs/core/lib/modifiers/offset';
import flipModifier from '@popperjs/core/lib/modifiers/flip';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
  children: React.ReactNode;
  flipOptions: typeof flipModifier['options'];
  offsetOptions: typeof offsetModifier['options'];
  className?: string;
};

const Popover: React.FC<Props> = ({
  selectedFlowNodeRef,
  children,
  flipOptions,
  offsetOptions,
  className,
}) => {
  const popoverElementRef = useRef<HTMLDivElement | null>(null);
  const [arrow, setArrow] = useState<HTMLElement | null>(null);

  const {styles, attributes} = usePopper(
    selectedFlowNodeRef,
    popoverElementRef.current,
    {
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

  return selectedFlowNodeRef !== null
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
