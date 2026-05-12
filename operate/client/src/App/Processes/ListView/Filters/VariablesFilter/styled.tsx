/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {createGlobalStyle} from 'styled-components';
import {IconButton as BaseIconButton} from '@carbon/react';
import {styles} from '@carbon/elements';
import {IconContainer, IconButton} from 'modules/components/IconInput/styled';

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

  ${IconContainer} {
    top: 0;
    bottom: auto;

    ${IconButton} {
      margin-top: 0;
    }
  }
`;

const DimmedParentModalStyle = createGlobalStyle`
  .variable-filter-modal--dimmed .cds--modal-container {
    opacity: 0.3;
    pointer-events: none;
    transition: opacity 150ms ease-in-out;
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

const ModeToggleContainer = styled.div`
  margin-top: var(--cds-spacing-05);
  padding-top: var(--cds-spacing-05);
  border-top: 1px solid var(--cds-border-subtle);
  opacity: 0.7;
`;

const BackButtonContent = styled.span`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const ExpandedEditorSection = styled.div<{$isOpen: boolean}>`
  overflow: hidden;
  max-height: ${({$isOpen}) => ($isOpen ? '50vh' : '0')};
  opacity: ${({$isOpen}) => ($isOpen ? 1 : 0)};
  transition:
    max-height 300ms ease-in-out,
    opacity 200ms ease-in-out;
  border-top: ${({$isOpen}) =>
    $isOpen ? '1px solid var(--cds-border-subtle)' : 'none'};
  margin-top: ${({$isOpen}) => ($isOpen ? 'var(--cds-spacing-05)' : '0')};
`;

const ExpandedEditorHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--cds-spacing-03) 0;

  h4 {
    margin: 0;
    ${styles.headingCompact01};
  }
`;

export {
  ModalContent,
  Description,
  FilterRow,
  ValueFieldContainer,
  DeleteButton,
  ConditionList,
  ConditionItem,
  DimmedParentModalStyle,
  ModeToggleContainer,
  BackButtonContent,
  ExpandedEditorSection,
  ExpandedEditorHeader,
};
