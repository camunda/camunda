/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {StructuredListCell, StructuredListRow} from '@carbon/react';

const IconText = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const FirstColumn = styled(StructuredListCell)`
  min-width: 200px;
`;

const VerticallyAlignedRow = styled(StructuredListRow)`
  .cds--structured-list-td {
    vertical-align: middle;
  }
`;

const ParagraphWithIcon = styled.p`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const TitleListCell = styled.h5`
  padding: 15px 0;
`;

const IconTextWithTopDivider = styled(IconText)`
  margin-top: var(--cds-spacing-03);
`;

export {
  IconText,
  FirstColumn,
  VerticallyAlignedRow,
  ParagraphWithIcon,
  TitleListCell,
  IconTextWithTopDivider,
};
