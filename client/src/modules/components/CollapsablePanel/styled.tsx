/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import styled from 'styled-components';
import {ReactComponent as LeftBar} from 'modules/icons/left-bar.svg';

const ExpandedPanel = styled.div`
  width: 478px;
  height: 100%;
`;

const CollapsedPanel = styled.div`
  width: 57px;
  height: 100%;
  cursor: pointer;
`;

const Title = styled.div`
  margin-top: 30px;
  transform: rotate(-90deg);
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

const Container = styled.div`
  width: fit-content;
  border: solid ${({theme}) => theme.colors.ui05};
  border-width: 0 0 1px 1px;
`;

export {
  ExpandedPanel,
  CollapsedPanel,
  Title,
  CollapseButton,
  LeftIcon,
  Container,
};
