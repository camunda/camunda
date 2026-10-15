/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import type { ProvisionedGroup } from "src/utility/api/groups";
import EntityDetail from "src/components/entityDetailV2";
import AttributesSection from "src/components/attributesSectionV2";
import useTranslate from "src/utility/localization";
import { formatDateTime } from "src/utility/format/provisioning";

type GroupDetailsProps = {
  group: ProvisionedGroup;
};

const GroupDetails: FC<GroupDetailsProps> = ({ group }) => {
  const { t } = useTranslate("groups");

  return (
    <>
      <EntityDetail
        label={t("groupDetails")}
        data={[
          { label: t("groupId"), value: group.groupId },
          { label: t("groupName"), value: group.name },
          ...(group.description
            ? [{ label: t("description"), value: group.description }]
            : []),
          ...(group.externalId
            ? [{ label: t("externalId"), value: group.externalId }]
            : []),
          { label: t("created"), value: formatDateTime(group.createdAt) },
          {
            label: group.externalId ? t("lastSynced") : t("lastModified"),
            value: formatDateTime(group.lastModifiedAt),
          },
        ]}
      />
      <AttributesSection attributes={group.attributes} />
    </>
  );
};

export default GroupDetails;
