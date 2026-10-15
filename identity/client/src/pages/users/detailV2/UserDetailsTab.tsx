/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { ProvisionedUser } from "src/pages/users/types";
import UserStatus from "src/pages/users/UserStatusBadgeV2";
import { FC } from "react";
import EntityDetail from "src/components/entityDetailV2";
import useTranslate from "src/utility/localization";
import { formatDateTime } from "src/utility/format/provisioning";
import AttributesSection from "src/components/attributesSectionV2";

type UserDetailsProps = {
  user: ProvisionedUser;
  loading: boolean;
};

const UserDetails: FC<UserDetailsProps> = ({ user, loading }) => {
  const { t } = useTranslate("users");

  return (
    <>
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
          { label: t("status"), value: <UserStatus user={user} /> },
          ...(user.source ? [{ label: t("source"), value: user.source }] : []),
          ...(user.externalId
            ? [{ label: t("externalId"), value: user.externalId }]
            : []),
          { label: t("created"), value: formatDateTime(user.createdAt) },
          {
            label: user.externalId ? t("lastSynced") : t("lastModified"),
            value: formatDateTime(user.lastModifiedAt),
          },
        ]}
        loading={loading}
      />
      <AttributesSection attributes={user.attributes} />
    </>
  );
};

export default UserDetails;
