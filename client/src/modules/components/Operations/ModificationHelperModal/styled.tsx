/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as HelpDiagram_Dark} from 'modules/components/Icon/HelpDiagram_Dark_Carbon.svg';
import {ReactComponent as HelpDiagram_Light} from 'modules/components/Icon/HelpDiagram_Light_Carbon.svg';
import {styles} from '@carbon/elements';
import {Add, Error, ArrowRight} from '@carbon/react/icons';
import {Checkbox as BaseCheckbox, Stack} from '@carbon/react';

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

const Checkbox = styled(BaseCheckbox)`
  margin-left: var(--cds-spacing-05);
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
  Checkbox,
};
