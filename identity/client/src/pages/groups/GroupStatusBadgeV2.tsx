/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Badge } from "@camunda/design-system";
import useTranslate from "src/utility/localization";

const GroupStatusBadge: FC = () => {
  const { t } = useTranslate("groups");

  return <Badge variant="neutral">{t("provisioned")}</Badge>;
};

export default GroupStatusBadge;
