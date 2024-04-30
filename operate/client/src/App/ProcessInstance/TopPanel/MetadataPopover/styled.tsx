/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {Popover as BasePopover} from 'modules/components/Popover';

const Popover = styled(BasePopover)`
  z-index: 5;
  width: 354px;
  padding: var(--cds-spacing-03) var(--cds-spacing-05) var(--cds-spacing-05);
`;

const Content = styled.div`
  ${styles.label02}
`;

const Divider = styled.hr`
  margin: var(--cds-spacing-03) 0 0 0;
  height: 1px;
  border: none;
  border-top: solid 1px var(--cds-border-subtle-01);
`;

const SummaryDataKey = styled.dt`
  ${styles.label01};
  white-space: nowrap;
`;

type Props = {
  $lineClamp?: number;
};

const SummaryDataValue = styled.dd<Props>`
  ${({$lineClamp}) => css`
    ${styles.label02};
    ${$lineClamp !== undefined &&
    css`
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: ${$lineClamp};
      overflow: hidden;
    `}
  `}
`;

export {Popover, Content, Divider, SummaryDataKey, SummaryDataValue};
