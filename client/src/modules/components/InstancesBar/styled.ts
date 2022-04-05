/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

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
} as const;

type WrapperProps = {
  size: 'small' | 'medium' | 'large';
};

const Wrapper = styled.div<WrapperProps>`
  ${({theme, size}) => {
    return css`
      display: flex;
      padding: 0;
      color: ${theme.colors.text02};
      font-family: IBM Plex Sans;
      line-height: 1.71;
      ${FONT_STYLES[size]}
    `;
  }}
`;

const greyTextStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.modules.instancesBar.greyTextStyle;

  return css`
    color: ${colors.color};
    opacity: 0.55;
  `;
};

type IncidentsCountProps = {
  hasIncidents?: boolean;
};

const IncidentsCount = styled.div<IncidentsCountProps>`
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

type ActiveCountProps = {
  hasActive?: boolean;
};

const ActiveCount = styled.div<ActiveCountProps>`
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

type BarContainerProps = {
  height: number;
};

const BarContainer = styled.div<BarContainerProps>`
  ${({height}) => {
    return css`
      position: relative;
      > div {
        height: ${height}px;
      }
    `;
  }}
`;

type BarProps = {
  hasActive?: boolean;
};

const Bar = styled.div<BarProps>`
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
