/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Badge } from "@camunda/design-system";
import SourceBadge from "src/components/sourceTagV2";
import useTranslate from "src/utility/localization";

const GroupStatusBadge: FC<{ source?: string | null }> = ({ source }) => {
  const { t } = useTranslate("groups");

  return (
    <span className="inline-flex items-center gap-1">
      <Badge variant="neutral">{t("provisioned")}</Badge>
      <SourceBadge source={source} />
    </span>
  );
};

export default GroupStatusBadge;
