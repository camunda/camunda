/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import HelpDiagram_Dark from 'modules/components/Icon/HelpDiagram_Dark_Carbon.svg?react';
import HelpDiagram_Light from 'modules/components/Icon/HelpDiagram_Light_Carbon.svg?react';
import {styles} from '@carbon/elements';
import {Add, Error, ArrowRight} from '@carbon/react/icons';
import {Stack} from '@carbon/react';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  p {
    margin: var(--cds-spacing-03) 0;
    ${styles.bodyShort01};
  }
`;

const Modifications = styled(Stack)`
  margin: var(--cds-spacing-04) 0 var(--cds-spacing-04) var(--cds-spacing-09);
`;

const Modification = styled.p`
  display: flex;
  ${styles.bodyShort01};
`;

const ModificationType = styled.span`
  color: var(--cds-link-primary);
  ${styles.bodyCompact01};

  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  min-width: var(--cds-spacing-12);
`;

const iconStyle = css`
  margin-right: var(--cds-spacing-05);
  color: var(--cds-link-primary);
`;

const AddIcon = styled(Add)`
  ${iconStyle}
`;

const CancelIcon = styled(Error)`
  ${iconStyle}
`;

const MoveIcon = styled(ArrowRight)`
  ${iconStyle}
`;

const diagramStyles = css`
  align-self: center;
  margin-top: var(--cds-spacing-05);
`;

const DiagramLight = styled(HelpDiagram_Light)`
  ${diagramStyles}
`;

const DiagramDark = styled(HelpDiagram_Dark)`
  ${diagramStyles}
`;

export {
  ModificationType,
  Modification,
  AddIcon,
  CancelIcon,
  MoveIcon,
  DiagramLight,
  DiagramDark,
  Container,
  Modifications,
};
