/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';

/**
 * Shine Border overlay for BPMN agent elements.
 * Recreates the Magic UI ShineBorder effect:
 * radial-gradient + animated background-position + mask to show only the border.
 */

const shine = keyframes`
  0% {
    background-position: 0% 0%;
  }
  50% {
    background-position: 100% 100%;
  }
  100% {
    background-position: 0% 0%;
  }
`;

const ShineBorder = styled.div<{$w: number; $h: number; $borderWidth: number}>`
  position: absolute;
  width: ${(p) => p.$w}px;
  height: ${(p) => p.$h}px;
  pointer-events: none;
  border-radius: 10px;
  will-change: background-position;

  background-image: radial-gradient(
    transparent,
    transparent,
    #a07cfe,
    #8a3ffc,
    #d4bbff,
    transparent,
    transparent
  );
  background-size: 300% 300%;
  animation: ${shine} 8s linear infinite;

  padding: ${(p) => p.$borderWidth}px;
  mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  -webkit-mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
`;

type Props = {
  container: HTMLElement;
  elementId: string;
};

const ShineBorderOverlay: React.FC<Props> = ({container, elementId}) => {
  const [dims, setDims] = useState<{width: number; height: number} | null>(
    null,
  );

  useEffect(() => {
    const svgGroup = document.querySelector(
      `[data-element-id="${elementId}"] .djs-visual`,
    );
    if (svgGroup) {
      const rect = svgGroup.querySelector('rect');
      if (rect) {
        const w = parseFloat(rect.getAttribute('width') ?? '0');
        const h = parseFloat(rect.getAttribute('height') ?? '0');
        if (w > 0 && h > 0) {
          setDims({width: w, height: h});
        }
      }
    }
  }, [elementId]);

  if (!dims) {
    return null;
  }

  return createPortal(
    <ShineBorder $w={dims.width} $h={dims.height} $borderWidth={2} />,
    container,
  );
};

export {ShineBorderOverlay};
