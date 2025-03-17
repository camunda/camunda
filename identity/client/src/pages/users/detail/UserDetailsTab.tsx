/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import EntityDetail from "src/components/entityDetail";
import useTranslate from "src/utility/localization";
import { User } from "src/utility/api/users";

type UserDetailsProps = {
  user: User;
  loading: boolean;
};

const UserDetails: FC<UserDetailsProps> = ({ user, loading }) => {
  const { t } = useTranslate("users");

  return (
    <EntityDetail
      label={t("userDetails")}
      data={[
        {
          label: t("name"),
          value: user.name,
        },
        {
          label: t("username"),
          value: user.username,
        },
        { label: t("email"), value: user.email || "-" },
      ]}
      loading={loading}
    />
  );
};

export default UserDetails;
