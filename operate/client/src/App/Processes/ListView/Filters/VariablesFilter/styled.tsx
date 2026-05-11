/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {IconButton as BaseIconButton} from '@carbon/react';
import {styles} from '@carbon/elements';

const ModalContent = styled.div`
  min-height: 350px;
`;

const Description = styled.p`
  margin: 0;
  ${styles.bodyShort01};
`;

const FilterRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 170px 1fr auto;
  gap: var(--cds-spacing-05);
  align-items: start;
`;

const ValueFieldContainer = styled.div`
  position: relative;
`;

const DeleteButton = styled(BaseIconButton)<{$hidden?: boolean}>`
  flex-shrink: 0;
  visibility: ${({$hidden}) => ($hidden ? 'hidden' : 'visible')};
`;

const ConditionList = styled.ul`
  margin: 0;
  padding: 0;
  list-style: none;
`;

const ConditionItem = styled.li`
  ${styles.bodyShort01};
`;

export {
  ModalContent,
  Description,
  FilterRow,
  ValueFieldContainer,
  DeleteButton,
  ConditionList,
  ConditionItem,
};
