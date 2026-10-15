/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Tag } from "@carbon/react";
import SourceTag from "src/components/sourceTag";
import useTranslate from "src/utility/localization";

const GroupStatusTag: FC<{ source?: string | null }> = ({ source }) => {
  const { t } = useTranslate("groups");

  return (
    <>
      <Tag type="blue" size="sm">
        {t("provisioned")}
      </Tag>
      <SourceTag source={source} />
    </>
  );
};

export default GroupStatusTag;
