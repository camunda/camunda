/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MouseEvent, MouseEventHandler, RefObject, useState} from 'react';

type TwoCoordinates = [number, number];

export interface UseAttachedMenuReturn {
  open: boolean;
  x: TwoCoordinates;
  y: TwoCoordinates;
  handleClick: () => void;
  handleMousedown: MouseEventHandler;
  handleClose: () => void;
}

export function useAttachedMenu(
  anchor: RefObject<HTMLElement | null> | HTMLElement
): UseAttachedMenuReturn {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<[TwoCoordinates, TwoCoordinates]>([
    [-1, -1],
    [-1, -1],
  ]);

  function openMenu() {
    const anchorEl = 'current' in anchor ? anchor?.current : anchor;

    if (anchorEl) {
      const {left, top, right, bottom} = anchorEl.getBoundingClientRect();

      setPosition([
        [left, right],
        [top, bottom],
      ]);
    }

    setOpen(true);
  }

  function closeMenu() {
    setOpen(false);
  }

  function handleClick() {
    if (open) {
      closeMenu();
    } else {
      openMenu();
    }
  }

  function handleMousedown(e: MouseEvent) {
    e.preventDefault();
  }

  return {
    open,
    x: position[0],
    y: position[1],
    handleClick,
    handleMousedown,
    handleClose: closeMenu,
  };
}
