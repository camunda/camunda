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
import {IconContainer, IconButton} from 'modules/components/IconInput/styled';

const ModalContent = styled.div`
  position: relative;
  min-height: 350px;
  overflow: hidden;

  && {
    margin-bottom: 0;
  }
`;

const Description = styled.p`
  ${styles.bodyShort01};

  && {
    margin: 0;
  }
`;

const FilterRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 170px 1fr auto;
  gap: var(--cds-spacing-05);
  align-items: start;
`;

const ValueFieldContainer = styled.div`
  position: relative;

  ${IconContainer} {
    top: 0;
    bottom: auto;

    ${IconButton} {
      margin-top: 0;
    }
  }
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
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
`;

const EditorToolbar = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: var(--cds-spacing-03);
  padding-bottom: var(--cds-spacing-03);
`;

const ConditionRowsScroll = styled.div`
  max-height: clamp(7.5rem, calc(84vh - 22rem), 24rem);
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
`;

export {
  ModalContent,
  Description,
  FilterRow,
  ValueFieldContainer,
  DeleteButton,
  ConditionList,
  ConditionItem,
  EditorToolbar,
  ConditionRowsScroll,
};
