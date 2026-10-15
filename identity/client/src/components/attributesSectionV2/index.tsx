/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useId } from "react";
import useTranslate from "src/utility/localization";
import { getAttributeEntries } from "src/utility/format/provisioning";

type AttributesSectionProps = {
  attributes?: Record<string, unknown> | null;
};

/** Read-only name/value list of provisioned attributes; hidden when empty. */
const AttributesSection: FC<AttributesSectionProps> = ({ attributes }) => {
  const { t } = useTranslate("components");
  const headingId = useId();
  const entries = getAttributeEntries(attributes);

  if (entries.length === 0) return null;

  return (
    <section aria-labelledby={headingId} className="mt-6">
      <h3 id={headingId} className="mb-2 text-lg font-medium">
        {t("attributes")}
      </h3>
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="shadow-[inset_0_-1px_0_var(--border)]">
            <th className="w-64 py-3 pr-4 font-medium">{t("attributeName")}</th>
            <th className="py-3 font-medium">{t("attributeValue")}</th>
          </tr>
        </thead>
        <tbody>
          {entries.map(([name, value]) => (
            <tr
              key={name}
              className="align-top shadow-[inset_0_-1px_0_var(--border)]"
            >
              <td className="whitespace-nowrap py-3 pr-4">{name}</td>
              <td className="py-3">
                {value.kind === "json" ? (
                  <pre className="m-0 overflow-x-auto rounded border border-[var(--border)] p-2 font-mono text-sm">
                    <code>{value.text}</code>
                  </pre>
                ) : (
                  value.text
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
};

export default AttributesSection;
