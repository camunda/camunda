/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Arrow = styled.div`
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
`;

const Popper = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.popoverOverlay.arrowStyle;

    return css`
      z-index: 5;
      &[data-popper-reference-hidden='true'] {
        visibility: hidden;
      }

      &[data-popper-placement^='top'] > ${Arrow} {
        bottom: 1px;
        &:before {
          left: calc(50% - 9px);
          border-top-color: ${colors.before.borderColor};
        }
        &:after {
          left: calc(50% - 8px);
          border-top-color: ${colors.after.borderColor};
        }
      }

      &[data-popper-placement^='bottom'] > ${Arrow} {
        top: -17px;
        &:before {
          left: calc(50% - 9px);
          border-bottom-color: ${colors.before.borderColor};
        }
        &:after {
          left: calc(50% - 8px);
          border-bottom-color: ${colors.after.borderColor};
        }
      }

      &[data-popper-placement^='right'] > ${Arrow} {
        left: -17px;
        &:before {
          top: calc(50% - 9px);
          border-right-color: ${colors.before.borderColor};
        }
        &:after {
          top: calc(50% - 8px);
          border-right-color: ${colors.after.borderColor};
        }
      }

      &[data-popper-placement^='left'] > ${Arrow} {
        right: 1px;
        &:before {
          top: calc(50% - 9px);
          border-left-color: ${colors.before.borderColor};
        }
        &:after {
          top: calc(50% - 8px);
          border-left-color: ${colors.after.borderColor};
        }
      }
    `;
  }}
`;

const Popover = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.popoverOverlay.popOver;
    const shadow = theme.shadows.modules.diagram.popoverOverlay.popOver;

    return css`
      width: 354px;
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.text02};
      font-size: 12px;
      border: 1px solid ${colors.borderColor};
      border-radius: 3px;
      box-shadow: ${shadow};
      padding: 11px;
      padding-top: 12px;
      cursor: auto;
    `;
  }}
`;

const Header = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
`;

const TitleStyles = css`
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  height: 17px;
  line-height: 17px;
`;

const Title = styled.h2`
  ${TitleStyles}
`;

const IncidentTitle = styled.h2`
  ${({theme}) => css`
    ${TitleStyles}
    color: ${theme.colors.incidentsAndErrors};
  `}
`;

const Divider = styled.hr`
  ${({theme}) => css`
    margin: 12px 0 17px 0;
    height: 1px;
    border: none;
    border-top: solid 1px
      ${theme.colors.modules.diagram.popoverOverlay.popOver.borderColor};
  `}
`;

const PeterCaseSummaryHeader = styled.div`
  font-weight: bold;
  text-align: center;
  white-space: nowrap;
`;

const PeterCaseSummaryBody = styled.div`
  margin-top: 3px;
  text-align: center;
  width: 100%;
`;

const SummaryDataKey = styled.dt`
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  height: 14px;
  line-height: 14px;
  margin-bottom: 4px;
`;

const SummaryDataValue = styled.dd`
  font-size: 12px;
  margin-left: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  height: 15px;
  line-height: 15px;
  margin-bottom: 8px;
`;

export {
  Arrow,
  Popper,
  Popover,
  Header,
  Title,
  Divider,
  IncidentTitle,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  SummaryDataKey,
  SummaryDataValue,
};
