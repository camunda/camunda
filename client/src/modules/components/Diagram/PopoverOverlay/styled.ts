/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {
  css,
  createGlobalStyle,
  ThemedInterpolationFunction,
} from 'styled-components';

import Modal from 'modules/components/Modal';
import {PopoverPosition} from '../service';

type Props = {
  side?: PopoverPosition['side'];
};

const arrowStyle: ThemedInterpolationFunction<Props> = ({side, theme}) => {
  const colors = theme.colors.modules.diagram.popoverOverlay.arrowStyle;

  switch (side) {
    case 'BOTTOM':
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          bottom: 100%;
        }

        &:before {
          border-bottom-color: ${colors.before.borderColor};
          left: calc(50% - 9px);
        }

        &:after {
          border-bottom-color: ${colors.after.borderColor};
          left: calc(50% - 8px);
        }
      `;

    case 'LEFT':
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          left: 100%;
        }

        &:before {
          border-left-color: ${colors.before.borderColor};
          top: calc(50% - 9px);
        }

        &:after {
          border-left-color: ${colors.after.borderColor};
          top: calc(50% - 8px);
        }
      `;

    case 'RIGHT':
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          right: 100%;
        }

        &:before {
          border-right-color: ${colors.before.borderColor};
          top: calc(50% - 9px);
        }

        &:after {
          border-right-color: ${colors.after.borderColor};
          top: calc(50% - 8px);
        }
      `;

    default:
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          top: 100%;
        }

        &:before {
          border-top-color: ${colors.before.borderColor};
          left: calc(50% - 9px);
        }

        &:after {
          border-top-color: ${colors.after.borderColor};
          left: calc(50% - 8px);
        }
      `;
  }
};

const Popover = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.popoverOverlay.popOver;
    const shadow = theme.shadows.modules.diagram.popoverOverlay.popOver;

    return css`
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      font-size: 12px;
      border: 1px solid ${colors.borderColor};
      border-radius: 3px;
      box-shadow: ${shadow};
      padding: 11px;
      padding-top: 12px;
      ${arrowStyle}
    `;
  }}
`;

const PopoverOverlayStyle = createGlobalStyle<Props>`
  .djs-overlay.djs-overlay-popover {
    z-index: 2;
    ${({side}) => {
      if (side === 'RIGHT') {
        return css`
          transform: translate(0, calc(-6px - 50%));
        `;
      }

      if (side === 'BOTTOM') {
        return css`
          transform: translate(calc(-6px - 50%), 0);
        `;
      }

      if (side === 'LEFT') {
        return css`
          transform: translate(-100%, calc(-6px - 50%));
        `;
      }

      return css`
        transform: translate(calc(-6px - 50%), -100%);
      `;
    }}
  }
`;

const PeterCaseSummaryHeader = styled.div`
  font-weight: bold;
  text-align: center;
  white-space: nowrap;
`;

const PeterCaseSummaryBody = styled.div`
  margin-top: 3px;
  text-align: center;
  width: 293px;
`;

const SummaryHeader = styled.div`
  margin-bottom: 12px;
  white-space: nowrap;
`;

const SummaryDataKey = styled.dt`
  white-space: nowrap;
  line-height: 18px;
  text-align: right;
  font-weight: normal;
`;

const SummaryDataValue = styled.dd`
  white-space: nowrap;
  line-height: 18px;
  margin: 0;
`;

const SummaryData = styled.dl`
  margin: 0;
  padding: 0;
  font-weight: 600;
  display: flex;
  flex-direction: column;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-column-gap: 6px;
  margin-bottom: 8px;
`;

const ModalBody = styled(Modal.Body)`
  padding: 0;
  position: relative;
  counter-reset: line;
  overflow: auto;

  & pre {
    margin: 0;
  }
`;

const CodeLine = styled.p`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.popoverOverlay.codeLine;
    const opacity = theme.opacity.modules.diagram.popoverOverlay.codeLine;

    return css`
      margin: 3px;
      margin-left: 0;
      line-height: 14px;
      color: ${colors.color};
      font-family: IBM Plex Mono;
      font-size: 14px;

      &:before {
        font-size: 12px;
        box-sizing: border-box;
        text-align: right;
        counter-increment: line;
        content: counter(line);
        color: ${colors.before.color};
        display: inline-block;
        width: 35px;
        opacity: ${opacity};
        padding-right: 11px;
        -webkit-user-select: none;
      }
    `;
  }}
`;

const LinesSeparator = styled.span`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.popoverOverlay.linesSeparator;

    return css`
      position: absolute;
      top: 0;
      left: 33px;
      height: 100%;
      width: 1px;
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

export {
  Popover,
  PopoverOverlayStyle,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  SummaryHeader,
  SummaryDataKey,
  SummaryDataValue,
  SummaryData,
  ModalBody,
  CodeLine,
  LinesSeparator,
};
