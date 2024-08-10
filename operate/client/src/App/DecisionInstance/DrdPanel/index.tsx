/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {drdStore} from 'modules/stores/drd';
import {useLayoutEffect, useRef} from 'react';
import {Container, Handle, Panel} from './styled';

const minWidth = 540;
const maxWidthRatio = 3 / 5;

type Props = {
  children: React.ReactNode;
};

const DrdPanel: React.FC<Props> = ({children}) => {
  const handleRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const startDimensions = useRef<{x?: number; width?: number}>({
    x: undefined,
    width: undefined,
  });

  const setPanelWidth = (width: number) => {
    if (containerRef.current === null) {
      return;
    }
    const maxWidth = Math.floor(document.body.clientWidth * maxWidthRatio);
    const newWidth = Math.min(Math.max(width, minWidth), maxWidth);

    containerRef.current.style.width = `${newWidth}px`;
  };

  useLayoutEffect(() => {
    const {panelWidth} = drdStore.state;

    if (panelWidth !== null) {
      setPanelWidth(panelWidth);
    }

    const handleResize = (event: MouseEvent) => {
      const {x: startX, width: startWidth} = startDimensions.current;

      if (
        containerRef.current === null ||
        startX === undefined ||
        startWidth === undefined
      ) {
        return;
      }

      const resizeWidth = startWidth - (event.clientX - startX);
      setPanelWidth(resizeWidth);
    };

    const handleResizeStart = (event: MouseEvent) => {
      if (containerRef.current === null) {
        return;
      }

      event.preventDefault();
      startDimensions.current.x = event.clientX;
      startDimensions.current.width = containerRef.current.clientWidth;
      document.body.style.cursor = 'ew-resize';
      containerRef.current?.classList.add('resizing');
      window.addEventListener('mouseup', handleResizeStop);
      window.addEventListener('mousemove', handleResize);
    };

    const handleResizeStop = () => {
      window.removeEventListener('mousemove', handleResize);
      window.removeEventListener('mouseup', handleResizeStop);
      containerRef.current?.classList.remove('resizing');
      document.body.style.cursor = 'unset';
      const width = containerRef.current?.clientWidth;

      if (width !== undefined) {
        drdStore.setPanelWidth(width);
      }
    };

    handleRef.current?.addEventListener('mousedown', handleResizeStart);
  }, []);

  return (
    <Container>
      <Panel data-testid="drd-panel" aria-label="drd panel" ref={containerRef}>
        {children}
      </Panel>
      <Handle ref={handleRef} />
    </Container>
  );
};

export {DrdPanel};
