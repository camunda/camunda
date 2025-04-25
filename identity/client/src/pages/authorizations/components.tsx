/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { TabListVertical, TabPanel } from "@carbon/react";
import {
  label02,
  layer01,
  sizeSmall,
  styles,
  spacing06,
  textSecondary,
  layerSelected01,
  spacing08,
} from "@carbon/elements";

export const TabsTitle = styled.p`
  position: relative;
  top: ${spacing06};
  font-size: ${label02.fontSize};
  font-weight: ${label02.fontWeight};
  letter-spacing: ${label02.letterSpacing};
  line-height: ${label02.lineHeight};
  color: ${textSecondary};
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
    top: ${spacing08};
    background: ${layer01};
    border: none;
    box-shadow: none;
    padding-right: ${spacing06};

    // Tab selector
    .cds--tabs__nav-item {
      background: ${layer01};
      margin-right: ${spacing06};
      height: ${sizeSmall};
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
      background: ${layerSelected01};
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
