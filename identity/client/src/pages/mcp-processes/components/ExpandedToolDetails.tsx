/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { styles } from "@carbon/elements";
import styled from "styled-components";
import useTranslate from "src/utility/localization";
import type { McpProcessTool } from "../useMcpProcessTools";

const SectionHeading = styled.h3`
  font-size: ${styles.heading01.fontSize};
  font-weight: ${styles.heading01.fontWeight};
  line-height: ${styles.heading01.lineHeight};
  letter-spacing: ${styles.heading01.letterSpacing};
`;

const SectionBody = styled.p`
  white-space: pre-wrap;
  word-break: break-word;
  max-inline-size: 80ch; // Keep descriptions at a readable line length

  &[data-fallback="true"] {
    font-style: italic;
    color: var(--cds-text-secondary);
  }

  &:not(:last-child) {
    margin-block-end: var(--cds-spacing-06);
  }
`;

export const ExpandedToolDetails: FC<{ tool: McpProcessTool }> = ({ tool }) => {
  const { t } = useTranslate("mcpProcesses");
  const properties = tool.toolProperties;

  return (
    <>
      <SectionHeading>{t("toolPurpose")}</SectionHeading>
      <SectionBody data-fallback={!properties.purpose}>
        {properties.purpose ?? t("informationMissing")}
      </SectionBody>

      <SectionHeading>{t("toolResults")}</SectionHeading>
      <SectionBody data-fallback={!properties.results}>
        {properties.results ?? t("informationMissing")}
      </SectionBody>

      <SectionHeading>{t("toolWhenToUse")}</SectionHeading>
      <SectionBody data-fallback={!properties.whenToUse}>
        {properties.whenToUse ?? t("informationMissing")}
      </SectionBody>

      <SectionHeading>{t("toolWhenNotToUse")}</SectionHeading>
      <SectionBody data-fallback={!properties.whenNotToUse}>
        {properties.whenNotToUse ?? t("informationMissing")}
      </SectionBody>
    </>
  );
};
