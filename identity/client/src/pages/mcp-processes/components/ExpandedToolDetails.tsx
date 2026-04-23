/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { heading01, spacing06 } from "@carbon/elements";
import styled from "styled-components";
import useTranslate from "src/utility/localization";
import type { McpProcessTool } from "../useMcpProcessTools";

const SectionHeading = styled.h3`
  font-size: ${heading01.fontSize};
  font-weight: ${heading01.fontWeight};
  line-height: ${heading01.lineHeight};
  letter-spacing: ${heading01.letterSpacing};
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
    margin-block-end: ${spacing06};
  }
`;

export const ExpandedToolDetails: FC<{ tool: McpProcessTool }> = ({ tool }) => {
  const { t } = useTranslate("mcpProcesses");
  const data = tool.toolData;

  return (
    <>
      <SectionHeading>{t("toolPurpose")}</SectionHeading>
      <SectionBody data-fallback={!data.purpose}>
        {data.purpose ?? t("informationMissing")}
      </SectionBody>

      <SectionHeading>{t("toolResults")}</SectionHeading>
      <SectionBody data-fallback={!data.results}>
        {data.results ?? t("informationMissing")}
      </SectionBody>

      <SectionHeading>{t("toolWhenToUse")}</SectionHeading>
      <SectionBody data-fallback={!data.whenToUse}>
        {data.whenToUse ?? t("informationMissing")}
      </SectionBody>

      <SectionHeading>{t("toolWhenNotToUse")}</SectionHeading>
      <SectionBody data-fallback={!data.whenNotToUse}>
        {data.whenNotToUse ?? t("informationMissing")}
      </SectionBody>
    </>
  );
};
