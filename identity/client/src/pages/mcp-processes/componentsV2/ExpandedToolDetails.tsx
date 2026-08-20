/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { cn, Heading, Text } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import type { McpProcessTool } from "../useMcpProcessTools";

export const ExpandedToolDetails: FC<{ tool: McpProcessTool }> = ({ tool }) => {
  const { t } = useTranslate("mcpProcesses");
  const properties = tool.toolProperties;

  return (
    <>
      <Heading as="h3" variant="heading-xs">
        {t("toolPurpose")}
      </Heading>
      <Description
        content={properties.purpose}
        fallback={t("informationMissing")}
      />

      <Heading as="h3" variant="heading-xs">
        {t("toolResults")}
      </Heading>
      <Description
        content={properties.results}
        fallback={t("informationMissing")}
      />

      <Heading as="h3" variant="heading-xs">
        {t("toolWhenToUse")}
      </Heading>
      <Description
        content={properties.whenToUse}
        fallback={t("informationMissing")}
      />

      <Heading as="h3" variant="heading-xs">
        {t("toolWhenNotToUse")}
      </Heading>
      <Description
        content={properties.whenNotToUse}
        fallback={t("informationMissing")}
        isLast
      />
    </>
  );
};

const Description: FC<{
  content: string | null;
  fallback: string;
  isLast?: boolean;
}> = ({ content, fallback, isLast }) => {
  return (
    <Text
      as="p"
      className={cn("max-w-[80ch] whitespace-pre-wrap wrap-break-word", {
        "mb-6": !isLast,
        "italic text-neutral-foreground-subtle": content === null,
      })}
    >
      {content ?? fallback}
    </Text>
  );
};
