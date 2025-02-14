import styled from "styled-components";
import { TabListVertical, TabPanel } from "@carbon/react";
import { label02, textSecondary } from "@carbon/elements";

export const TabsTitle = styled.p`
  position: relative;
  top: 30px;
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
    margin-top: 50px;
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

export const LoadingWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
`;
