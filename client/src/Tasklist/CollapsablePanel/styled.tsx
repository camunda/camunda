/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import styled from 'styled-components';
import {ReactComponent as LeftBar} from 'modules/icons/left-bar.svg';

const ExpandedPanel = styled.div`
  height: 100%;
`;

const CollapsedPanel = styled.div`
  cursor: pointer;
  height: 100%;
`;

const Title = styled.div`
  transform: rotate(-90deg);
  transform-origin: 0 0;
  position: absolute;
  top: 68px;
  left: 16px;
  font-size: 15px;
  font-weight: bold;
  color: ${({theme}) => theme.colors.ui06};
`;

interface CollapseButtonProps {
  onClick: () => void;
}

const CollapseButton = styled.button<CollapseButtonProps>`
  padding: 8px 9px 8px 11px;
  border-left: 1px solid ${({theme}) => theme.colors.ui05};
  background: transparent;
`;

const LeftIcon = styled(LeftBar)`
  width: 16px;
  height: 16px;
  opacity: 0.9;
  color: ${({theme}) => theme.colors.ui06};
`;

interface ContainerProps {
  isExpanded: boolean;
}

const Container = styled.div<ContainerProps>`
  min-width: ${({isExpanded}) => (isExpanded ? '478px' : '57px')};
  border: 1px solid ${({theme}) => theme.colors.ui05};
  position: relative;
`;

export {
  ExpandedPanel,
  CollapsedPanel,
  Title,
  CollapseButton,
  LeftIcon,
  Container,
};
