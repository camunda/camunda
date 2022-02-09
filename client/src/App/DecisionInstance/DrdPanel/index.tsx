/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useLayoutEffect, useRef} from 'react';
import {Container, Handle, Panel} from './styled';

const minWidth = 540;
const maxWidthRatio = 3 / 5;

const DrdPanel: React.FC = ({children}) => {
  const handleRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const startDimensions = useRef<{x?: number; width?: number}>({
    x: undefined,
    width: undefined,
  });

  useLayoutEffect(() => {
    const handleResize = (event: MouseEvent) => {
      const {x: startX, width: startWidth} = startDimensions.current;

      if (
        containerRef.current === null ||
        startX === undefined ||
        startWidth === undefined
      ) {
        return;
      }

      const newWidth = startWidth - (event.clientX - startX);
      const maxWidth = document.body.clientWidth * maxWidthRatio;

      if (newWidth < minWidth || newWidth > maxWidth) {
        return;
      }

      containerRef.current.style.width = `${newWidth}px`;
    };

    const handleResizeStart = (event: MouseEvent) => {
      if (containerRef.current === null) {
        return;
      }

      event.preventDefault();
      startDimensions.current.x = event.clientX;
      startDimensions.current.width = containerRef.current.clientWidth;
      document.body.style.cursor = 'ew-resize';
      window.addEventListener('mouseup', handleResizeStop);
      window.addEventListener('mousemove', handleResize);
    };

    const handleResizeStop = () => {
      window.removeEventListener('mousemove', handleResize);
      window.removeEventListener('mouseup', handleResizeStop);
      document.body.style.cursor = 'unset';
    };

    handleRef.current?.addEventListener('mousedown', handleResizeStart);
  }, []);

  return (
    <Container>
      <Handle ref={handleRef} />
      <Panel data-testid="drd-panel" ref={containerRef}>
        {children}
      </Panel>
    </Container>
  );
};

export {DrdPanel};
