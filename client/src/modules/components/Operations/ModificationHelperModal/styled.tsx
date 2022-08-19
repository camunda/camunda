/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {ReactComponent as Plus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';
import {ReactComponent as Move} from 'modules/components/Icon/move.svg';
import {ReactComponent as HelpDiagram_Dark} from 'modules/components/Icon/HelpDiagram_Dark.svg';
import {ReactComponent as HelpDiagram_Light} from 'modules/components/Icon/HelpDiagram_Light.svg';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  margin-top: 16px;
`;

const Modifications = styled.div`
  margin: 7px 0 12px 40px;
`;

const Modification = styled.p`
  margin: 10px 0;
`;

const Text = styled.p`
  margin: 8px 0;
`;

const ModificationType = styled.span`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.helperModal.modificationType;

    return css`
      color: ${colors.color};
      font-weight: 500;
    `;
  }}
`;

const iconStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors =
    theme.colors.processInstance.modifications.helperModal.modificationType;

  return css`
    margin-right: 10px;
    float: left;
    color: ${colors.color};
  `;
};

const AddIcon = styled(Plus)`
  ${iconStyle}
`;

const CancelIcon = styled(Stop)`
  ${iconStyle}
`;

const MoveIcon = styled(Move)`
  ${iconStyle}
`;

const diagramStyles = css`
  align-self: center;
  margin-top: 30px;
`;

const DiagramLight = styled(HelpDiagram_Light)`
  ${diagramStyles}
`;

const DiagramDark = styled(HelpDiagram_Dark)`
  ${diagramStyles}
`;

const ModificationModalFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-left: 20px;
`;

export {
  ModificationType,
  ModificationModalFooter,
  Modification,
  AddIcon,
  CancelIcon,
  MoveIcon,
  DiagramLight,
  DiagramDark,
  Container,
  Modifications,
  Text,
};
