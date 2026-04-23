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
import Modal, { UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { type McpProcessTool } from "../useMcpProcessTools";

const SectionHeading = styled.h3`
  font-size: ${heading01.fontSize};
  font-weight: ${heading01.fontWeight};
  line-height: ${heading01.lineHeight};
  letter-spacing: ${heading01.letterSpacing};
`;

const SectionBody = styled.p`
  white-space: pre-wrap;
  word-break: break-word;

  &:not(:last-child) {
    margin-block-end: ${spacing06};
  }
`;

const DetailsModal: FC<UseEntityModalProps<McpProcessTool>> = ({
  open,
  onClose,
  entity,
}) => {
  const { t } = useTranslate("mcpProcesses");
  const tool = entity.toolData;

  return (
    <Modal
      passiveModal
      preventCloseOnClickOutside
      open={open}
      onClose={onClose}
      headline={t("detailsHeadline", { toolName: entity.toolName })}
      size="md"
    >
      {/* Flat content to make use of Carbon's built-in modal content alignment:
      https://carbondesignsystem.com/components/modal/usage/#alignment */}
      <SectionHeading>{t("toolPurpose")}</SectionHeading>
      <SectionBody>{tool.purpose ?? t("informationMissing")}</SectionBody>

      <SectionHeading>{t("toolResults")}</SectionHeading>
      <SectionBody>{tool.results ?? t("informationMissing")}</SectionBody>

      <SectionHeading>{t("toolWhenToUse")}</SectionHeading>
      <SectionBody>{tool.whenToUse ?? t("informationMissing")}</SectionBody>

      <SectionHeading>{t("toolWhenNotToUse")}</SectionHeading>
      <SectionBody>{tool.whenNotToUse ?? t("informationMissing")}</SectionBody>
    </Modal>
  );
};

export default DetailsModal;
