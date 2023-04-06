/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export type Align = {
  center: number;
  left: number;
  right: number;
};

export type Position = 'top' | 'bottom';

export function getNonOverflowingValues(
  tooltip: HTMLElement,
  hoverElement: HTMLElement,
  align: keyof Align,
  position: Position
) {
  const {width, height} = tooltip.getBoundingClientRect();
  const hoverElementBox = hoverElement.getBoundingClientRect();
  const left: Align = {
    center: hoverElementBox.x + hoverElementBox.width / 2,
    left: hoverElementBox.x,
    right: hoverElementBox.x + hoverElementBox.width,
  };

  const newAlign = getNewAlign(align, width, left);
  const newPosition = getNewPosition(position, height + getTooltipMargin(tooltip), hoverElementBox);

  return {
    newAlign,
    newPosition,
    width,
    left: left[newAlign],
    top: hoverElementBox[newPosition],
  };
}

function getNewAlign(align: keyof Align, tooltipWidth: number, left: Align): keyof Align {
  const widthToArrow = align === 'center' ? tooltipWidth / 2 : tooltipWidth;
  const overflowingLeft = widthToArrow > left[align];
  const overflowingRight = left[align] + widthToArrow > getBody().clientWidth;
  if (overflowingLeft) {
    return 'left';
  } else if (overflowingRight) {
    return 'right';
  }
  return align;
}

function getNewPosition(position: Position, height: number, hoverElementBox: DOMRect): Position {
  const overflowingBottom =
    position === 'bottom' && hoverElementBox.bottom + height > getBody().clientHeight;
  const overflowingTop = position === 'top' && hoverElementBox.y - height < 0;
  if (overflowingBottom) {
    return 'top';
  } else if (overflowingTop) {
    return 'bottom';
  }

  return position;
}

function getTooltipMargin(tooltip: HTMLElement): number {
  const tooltipStyles = window.getComputedStyle(tooltip);
  const getProperty = (property: string) =>
    Number(tooltipStyles.getPropertyValue(property).match(/\d+/));

  return getProperty('margin-top') + getProperty('margin-bottom');
}

function getBody() {
  return document.fullscreenElement || document.body;
}
