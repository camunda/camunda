/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { TabListVertical, TabPanel } from "@carbon/react";
import { styles } from "@carbon/elements";

export const TabsTitle = styled.p`
  position: relative;
  top: var(--cds-spacing-06);
  font-size: ${styles.label02.fontSize};
  font-weight: ${styles.label02.fontWeight};
  letter-spacing: ${styles.label02.letterSpacing};
  line-height: ${styles.label02.lineHeight};
  color: var(--cds-text-secondary);
`;

export const TabsContainer = styled.div`
  .cds--css-grid {
    margin-inline: 0;
    padding-inline: 0;
    max-inline-size: none;
  }
`;

export const CustomTabListVertical = styled(TabListVertical)`
  // Tab selectors container
  &.cds--tabs--vertical {
    top: var(--cds-spacing-08);
    background: var(--cds-layer);
    border: none;
    box-shadow: none;
    padding-right: var(--cds-spacing-06);

    // Tab selector
    .cds--tabs__nav-item {
      background: var(--cds-layer);
      margin-right: var(--cds-spacing-06);
      height: var(--cds-spacing-07);
      border: none;
      box-shadow: none;
      font-weight: ${styles.headingCompact01.fontWeight};

      // Tab selector when not selected, disabled or hovered
      &:not(.cds--tabs__nav-item--selected):not(
          .cds--tabs__nav-item--disabled
        ):not(.cds--tabs__nav-item--hover-off):hover {
        box-shadow: none;
      }
    }

    // Tab selector when selected
    .cds--tabs__nav-item--selected {
      background: var(--cds-layer-selected);
    }
  }
`;

export const CustomTabPanel = styled(TabPanel)`
  padding: 0;
`;

export const LoadingWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
`;
