/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { CurrentUserWithSource } from "src/utility/api/authentication";
import useTranslate from "src/utility/localization";

const MISSING_VALUE = "–";

/** Label/value rows describing how the current user signed in and what they got. */
export const useSignInDetails = (
  user: CurrentUserWithSource,
): { key: string; label: string; value: string }[] => {
  const { t } = useTranslate("authentication");
  const list = (values?: string[] | null) =>
    values?.length ? values.join(", ") : t("none");

  return [
    {
      key: "loginSource",
      label: t("signedInVia"),
      value: user.loginSource || MISSING_VALUE,
    },
    {
      key: "provisionedBy",
      label: t("provisionedBy"),
      value: user.provisionedBy || t("notProvisioned"),
    },
    { key: "groups", label: t("userGroups"), value: list(user.groups) },
    { key: "roles", label: t("userRoles"), value: list(user.roles) },
    {
      key: "mappingRules",
      label: t("appliedMappingRules"),
      value: list(user.mappingRules),
    },
  ];
};
