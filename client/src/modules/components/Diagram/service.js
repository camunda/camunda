import {Colors, themeStyle} from 'modules/theme';
import {POPOVER_SIDE} from 'modules/constants';

export function getDiagramColors(theme) {
  return {
    defaultFillColor: themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04
    })({theme}),
    defaultStrokeColor: themeStyle({
      dark: Colors.darkDiagram,
      light: Colors.uiLight06
    })({theme})
  };
}

export function getPopoverPostion({diagramContainer, flowNode}) {
  const MIN_HEIGHT = 70;
  const MIN_WIDTH = 190;

  const containerBoundary = diagramContainer.getBoundingClientRect();
  const flowNodeBoundary = flowNode.getBoundingClientRect();
  const flowNodeBBox = flowNode.getBBox();

  const spaceToBottom =
    containerBoundary.top +
    containerBoundary.height -
    flowNodeBoundary.top -
    flowNodeBoundary.height;

  if (spaceToBottom > MIN_HEIGHT) {
    return {
      bottom: -16,
      left: flowNodeBBox.width / 2,
      side: POPOVER_SIDE.BOTTOM
    };
  }

  const spaceToLeft = flowNodeBoundary.left - containerBoundary.left;

  if (spaceToLeft > MIN_WIDTH) {
    return {
      left: -16,
      top: flowNodeBBox.height / 2,
      side: POPOVER_SIDE.LEFT
    };
  }

  const spaceToTop = flowNodeBoundary.top - containerBoundary.top;

  if (spaceToTop > MIN_HEIGHT) {
    return {
      top: -16,
      left: flowNodeBBox.width / 2,
      side: POPOVER_SIDE.TOP
    };
  }

  const spaceToRight =
    containerBoundary.width - spaceToLeft - flowNodeBoundary.width;

  if (spaceToRight > MIN_WIDTH) {
    return {
      top: flowNodeBBox.height / 2,
      right: -16,
      side: POPOVER_SIDE.RIGHT
    };
  }

  return {
    bottom: 16,
    left: flowNodeBBox.width / 2,
    side: POPOVER_SIDE.BOTTOM_MIRROR
  };
}
