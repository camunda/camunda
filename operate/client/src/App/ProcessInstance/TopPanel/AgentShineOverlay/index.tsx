/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {ShineBox} from './styled';

type Props = {
  container: HTMLElement;
  elementId: string;
};

type Size = {
  width: number;
  height: number;
  radius: number;
};

const AgentShineOverlay: React.FC<Props> = ({container, elementId}) => {
  const [size, setSize] = useState<Size | null>(null);

  useLayoutEffect(() => {
    const container = document.querySelector<SVGGraphicsElement>(
      `[data-element-id="${elementId}"] .djs-visual rect`,
    );
    if (container === null) {
      setSize(null);
      return;
    }

    const width = Number.parseFloat(container.getAttribute('width') ?? '0');
    const height = Number.parseFloat(container.getAttribute('height') ?? '0');
    const radius = Number.parseFloat(container.getAttribute('rx') ?? '0');
    setSize({
      width: Number.isFinite(width) ? width : 0,
      height: Number.isFinite(height) ? height : 0,
      radius: Number.isFinite(radius) ? radius : 0,
    });
  }, [elementId]);

  if (size === null) {
    return null;
  }

  return createPortal(
    <ShineBox
      data-testid={`agent-shine-overlay-${elementId}`}
      $width={size.width}
      $height={size.height}
      $radius={size.radius}
    />,
    container,
  );
};

export {AgentShineOverlay};
