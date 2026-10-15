/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Text } from "@camunda/design-system";
import type { CurrentUserWithSource } from "src/utility/api/authentication";
import useTranslate from "src/utility/localization";
import { useSignInDetails } from "../useSignInDetails";

const SignInDetails: FC<{ user: CurrentUserWithSource }> = ({ user }) => {
  const { t } = useTranslate("authentication");
  const rows = useSignInDetails(user);

  return (
    <div className="px-2 py-1.5">
      <Text
        as="span"
        variant="label-sm"
        className="text-neutral-foreground-subtle"
      >
        {t("signInDetails")}
      </Text>
      <dl className="mt-2 grid gap-1.5 break-words">
        {rows.map(({ key, label, value }) => (
          <div key={key}>
            <dt className="text-xs text-neutral-foreground-subtle">{label}</dt>
            <dd className="text-sm">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
};

export default SignInDetails;
