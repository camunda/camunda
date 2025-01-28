import styled from "styled-components";
import { TabListVertical, TabPanel } from "@carbon/react";

export const TabsTitle = styled.p`
  margin: 20px 0;
  font-weight: 500;
`;

export const CustomTabListVertical = styled(TabListVertical)`
  // Tab selectors container
  &.cds--tabs--vertical {
    background: var(--cds-layer-02);
    border: none;
    box-shadow: none;
    padding-right: 24px;

    // Tab selector
    .cds--tabs__nav-item {
      background: var(--cds-layer-02);
      border: none;
      margin-right: 24px;
      height: 32px;
      box-shadow: none;
      font-weight: 600;

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
