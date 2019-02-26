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

export function getPopoverPostion({
  diagramContainer,
  flowNode,
  minHeight,
  minWidth
}) {
  const containerBoundary = diagramContainer.getBoundingClientRect();
  const flowNodeBoundary = flowNode.getBoundingClientRect();
  const flowNodeBBox = flowNode.getBBox();

  // space between the bottom of the flow node and the end of the diagram container
  const spaceToBottom = containerBoundary.bottom - flowNodeBoundary.bottom;
  // space between the left of the flow node and the end of the diagram container
  const spaceToLeft = flowNodeBoundary.left - containerBoundary.left;
  // space between the top of the flow node and the end of the diagram container
  const spaceToTop = flowNodeBoundary.top - containerBoundary.top;
  // space between the right of the flow node and the end of the diagram container
  const spaceToRight =
    containerBoundary.width - spaceToLeft - flowNodeBoundary.width;

  // space to the left of the popover, if it gets position at the bottom/top of the flow node
  const veritcalPopoverSpaceToLeft =
    flowNodeBoundary.left + flowNodeBoundary.width / 2;

  // space to the right of the popover, if it gets position at the bottom/top of the flow node
  const verticalPopoverSpaceToRight =
    flowNodeBoundary.right + flowNodeBoundary.width / 2;

  // space to the bottom of the popover, if it gets position at the left/right of the flow node
  const horizontalPopoverSpaceToBottom =
    containerBoundary.bottom -
    flowNodeBoundary.bottom +
    flowNodeBoundary.height / 2;

  // space to the top of the popover, if it gets position at the left/right of the flow node
  const horizontalPopoverSpaceToTOP =
    flowNodeBoundary.top + flowNodeBoundary.height / 2;

  // can the popover be positioned at the bottom of the flow node?
  if (
    spaceToBottom > minHeight &&
    veritcalPopoverSpaceToLeft > minWidth / 2 &&
    verticalPopoverSpaceToRight > minWidth / 2
  ) {
    return {
      bottom: -16,
      left: flowNodeBBox.width / 2,
      side: POPOVER_SIDE.BOTTOM
    };
  }

  // can the popover be positioned at the left of the flow node?
  if (
    spaceToLeft > minWidth &&
    horizontalPopoverSpaceToBottom > minHeight / 2 &&
    horizontalPopoverSpaceToTOP > minHeight / 2
  ) {
    return {
      left: -16,
      top: flowNodeBBox.height / 2,
      side: POPOVER_SIDE.LEFT
    };
  }

  // can the popover be positioned at the top of the flow node?
  if (
    spaceToTop > minHeight &&
    veritcalPopoverSpaceToLeft > minWidth / 2 &&
    verticalPopoverSpaceToRight > minWidth / 2
  ) {
    return {
      top: -16,
      left: flowNodeBBox.width / 2,
      side: POPOVER_SIDE.TOP
    };
  }

  // can the popover be positioned at the right of the flow node?

  if (
    spaceToRight > minWidth &&
    horizontalPopoverSpaceToBottom > minHeight &&
    horizontalPopoverSpaceToTOP > minHeight
  ) {
    return {
      top: flowNodeBBox.height / 2,
      right: -16,
      side: POPOVER_SIDE.RIGHT
    };
  }

  // position the popover in a mirrored position (from bottom to top) at the bottom of the flow node
  return {
    bottom: 16,
    left: flowNodeBBox.width / 2,
    side: POPOVER_SIDE.BOTTOM_MIRROR
  };
}
