/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const colors = ({theme}) => {
  const colors = theme.colors.modules.skeleton;
  const opacity = theme.opacity.modules.skeleton;

  return css`
    background: ${colors.backgroundColor};
    opacity: ${opacity};
  `;
};

const BaseBlock = styled.div`
  ${colors}
`;

const BaseCircle = styled.div`
  border-radius: 50%;
  ${colors}
`;

export {BaseBlock, BaseCircle};
