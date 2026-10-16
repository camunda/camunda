/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Tag } from "@carbon/react";
import useTranslate from "src/utility/localization";

/** Names the SCIM source that provisioned an entity; renders nothing when empty. */
const SourceTag: FC<{ source?: string | null }> = ({ source }) => {
  const { t } = useTranslate("components");

  if (!source) return null;

  return (
    <Tag type="cool-gray" size="sm">
      {t("sourceTag", { source })}
    </Tag>
  );
};

export default SourceTag;
