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
import { isUserActive, ProvisionedUser } from "./types";

const UserStatusTag: FC<{ user: ProvisionedUser }> = ({ user }) => {
  const { t } = useTranslate("users");
  const active = isUserActive(user);

  return (
    <>
      <Tag type={active ? "green" : "gray"} size="sm">
        {active ? t("active") : t("deactivated")}
      </Tag>
      <SourceTag source={user.source} />
    </>
  );
};

export default UserStatusTag;
