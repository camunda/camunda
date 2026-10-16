/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import {
  CodeSnippet,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
} from "@carbon/react";
import useTranslate from "src/utility/localization";
import { getAttributeEntries } from "src/utility/format/provisioning";

type AttributesSectionProps = {
  attributes?: Record<string, unknown> | null;
};

/** Read-only name/value list of provisioned attributes; hidden when empty. */
const AttributesSection: FC<AttributesSectionProps> = ({ attributes }) => {
  const { t } = useTranslate("components");
  const entries = getAttributeEntries(attributes);

  if (entries.length === 0) return null;

  return (
    <section aria-labelledby="attributes-heading">
      <h3 id="attributes-heading">{t("attributes")}</h3>
      <StructuredListWrapper aria-label={t("attributes")}>
        <StructuredListHead>
          <StructuredListRow head>
            <StructuredListCell head>{t("attributeName")}</StructuredListCell>
            <StructuredListCell head>{t("attributeValue")}</StructuredListCell>
          </StructuredListRow>
        </StructuredListHead>
        <StructuredListBody>
          {entries.map(([name, value]) => (
            <StructuredListRow key={name}>
              <StructuredListCell noWrap>{name}</StructuredListCell>
              <StructuredListCell>
                {value.kind === "json" ? (
                  <CodeSnippet type="multi" hideCopyButton>
                    {value.text}
                  </CodeSnippet>
                ) : (
                  value.text
                )}
              </StructuredListCell>
            </StructuredListRow>
          ))}
        </StructuredListBody>
      </StructuredListWrapper>
    </section>
  );
};

export default AttributesSection;
