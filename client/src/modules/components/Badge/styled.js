/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {BADGE_TYPE} from 'modules/constants';

function getVariant({theme, type}) {
  const colors = theme.colors.modules.badge;

  switch (type) {
    case BADGE_TYPE.FILTERS:
      return css`
        background-color: ${theme.colors.filtersAndWarnings};
        color: ${colors.filters.color};
      `;
    case BADGE_TYPE.INCIDENTS:
      return css`
        background-color: ${theme.colors.incidentsAndErrors};
        color: ${theme.colors.white};
      `;
    default:
      return css`
        background-color: ${colors.default.backgroundColor};
        color: ${colors.default.color};
      `;
  }
}

const BaseBadge = styled.div`
  ${({theme, $isActive}) => {
    const opacity = theme.opacity.modules.badge;

    return css`
      display: inline-block;
      height: 17px;
      margin-left: 8px;
      font-size: 12px;
      font-weight: 600;
      line-height: 1.5;
      ${getVariant}
      ${$isActive
        ? ''
        : css`
            opacity: ${opacity};
          `}
    `;
  }}
`;

const Badge = styled(BaseBadge)`
  padding: 0 9px;
  min-width: 17px;
  border-radius: 8.5px;
`;

const BadgeCircle = styled(BaseBadge)`
  width: 17px;
  padding: 0;
  text-align: center;
  border-radius: 50%;
`;

export {Badge, BadgeCircle};
