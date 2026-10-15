/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { FormLabel } from "@carbon/react";
import type { CurrentUserWithSource } from "src/utility/api/authentication";
import useTranslate from "src/utility/localization";
import { useSignInDetails } from "./useSignInDetails";

const SignInDetails: FC<{ user: CurrentUserWithSource }> = ({ user }) => {
  const { t } = useTranslate("authentication");
  const rows = useSignInDetails(user);

  return (
    <div style={{ padding: "1rem", overflowWrap: "anywhere" }}>
      <FormLabel>{t("signInDetails")}</FormLabel>
      <dl style={{ margin: "0.5rem 0 0", fontSize: "12px" }}>
        {rows.map(({ key, label, value }) => (
          <div key={key} style={{ marginBottom: "0.5rem" }}>
            <dt style={{ opacity: 0.7 }}>{label}</dt>
            <dd style={{ margin: 0 }}>{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
};

export default SignInDetails;
