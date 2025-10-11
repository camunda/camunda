/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {ProgressBar as BaseProgressBar} from '@carbon/react';
import {OPERATION_ENTRY_HEIGHT} from './constants';

const Container = styled.li`
  padding: var(--cds-spacing-05);
  ${styles.bodyCompact01};
  border-bottom: 1px solid var(--cds-border-subtle-01);
  height: ${OPERATION_ENTRY_HEIGHT}px;
`;

const Header = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--cds-spacing-02);
`;

const Title = styled.h3`
  ${styles.productiveHeading01};
  margin: 0;
`;

const Details = styled.div`
  display: flex;
  align-items: flex-end;
  flex-direction: row;
  justify-content: space-between;
  padding-top: var(--cds-spacing-07);
`;

const PROGRESS_BAR_HEIGHT = 40;

const ProgressBar = styled(BaseProgressBar)`
  height: ${PROGRESS_BAR_HEIGHT}px;
  padding-top: var(
    --cds-spacing-06
  ); // empty label has 0.5rem margin-bottom, so this spacing should be 06 instead of 07
`;

const InvisibleProgressBar = styled.div`
  height: ${PROGRESS_BAR_HEIGHT}px;
`;

export {Title, Details, Header, Container, ProgressBar, InvisibleProgressBar};
