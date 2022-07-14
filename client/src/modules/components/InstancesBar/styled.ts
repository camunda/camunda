/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {styles} from '@carbon/elements';

type WrapperProps = {
  size: 'small' | 'medium' | 'large';
};

const getFontStyle: ThemedInterpolationFunction<WrapperProps> = ({
  size,
  theme,
}) => {
  const colors = theme.colors.modules.instancesBar.mediumTextStyle;

  return css`
    ${size === 'small' &&
    css`
      ${styles.label01};
      color: ${theme.colors.text01};
    `}
    ${size === 'medium' &&
    css`
      ${styles.label02};
      font-weight: 600;
      color: ${colors.color};
    `}

    ${size === 'large' &&
    css`
      ${styles.label02};
      font-size: 28px;
      color: ${theme.colors.text02};
    `}
  `;
};

const Wrapper = styled.div<WrapperProps>`
  ${({theme, size}) => {
    return css`
      display: flex;
      padding: 0;
      ${getFontStyle({size, theme})}
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
      top: 2px;
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
