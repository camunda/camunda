/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const FONT_STYLES = {
  small: css`
    font-weight: 400;
    font-size: 13px;
  `,
  medium: css`
    font-weight: 600;
    font-size: 14px;
  `,
  large: css`
    font-weight: 600;
    font-size: 30px;
  `,
};

const Wrapper = styled.div`
  ${({theme, size}) => {
    const colors = theme.colors.modules.instancesBar.wrapper;

    return css`
      display: flex;
      padding: 0;
      color: ${colors.color};
      font-family: IBMPlexSans;
      line-height: 1.71;
      ${FONT_STYLES[size]}
    `;
  }}
`;

const greyTextStyle = ({theme}) => {
  const colors = theme.colors.modules.instancesBar.greyTextStyle;

  return css`
    color: ${colors.color};
    opacity: 0.55;
  `;
};

const IncidentsCount = styled.div`
  ${({theme, hasIncidents}) => {
    return css`
      min-width: 96px;
      ${hasIncidents
        ? css`
            color: ${theme.colors.incidentsAndErrors};
          `
        : greyTextStyle}
    `;
  }}
`;

const ActiveCount = styled.div`
  ${({theme, hasActive}) => {
    return css`
      margin-left: auto;
      width: 139px;
      text-align: right;
      ${hasActive
        ? css`
            color: ${theme.colors.allIsWell};
            opacity: 0.8;
          `
        : greyTextStyle}
    `;
  }}
`;

const Label = styled.div`
  ${({theme}) => {
    const opacity = theme.opacity.modules.instancesBar.label;

    return css`
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      opacity: ${opacity};
    `;
  }}
`;

const BarContainer = styled.div`
  ${({height}) => {
    return css`
      position: relative;
      > div {
        height: ${height}px;
      }
    `;
  }}
`;

const Bar = styled.div`
  ${({theme, hasActive}) => {
    const opacity = theme.opacity.modules.instancesBar.bar.active;

    return css`
      ${hasActive
        ? css`
            background: ${theme.colors.allIsWell};
            opacity: 0.8;
          `
        : css`
            background: ${theme.colors.badge02};
            opacity: ${opacity};
          `}
    `;
  }}
`;

const IncidentsBar = styled.div`
  ${({theme}) => {
    return css`
      position: absolute;
      top: 0;
      background: ${theme.colors.incidentsAndErrors};
    `;
  }}
`;

export {
  Wrapper,
  IncidentsCount,
  ActiveCount,
  Label,
  BarContainer,
  Bar,
  IncidentsBar,
};
