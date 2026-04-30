/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {IconButton as BaseIconButton, Modal as BaseModal} from '@carbon/react';
import {styles} from '@carbon/elements';

const Modal = styled(BaseModal)`
  .cds--modal-content {
    min-height: 350px;
  }
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

const ConditionDropdownContainer = styled.div`
  width: 170px;
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
  Modal,
  Description,
  FilterRow,
  ConditionDropdownContainer,
  ValueFieldContainer,
  DeleteButton,
  ConditionList,
  ConditionItem,
};
