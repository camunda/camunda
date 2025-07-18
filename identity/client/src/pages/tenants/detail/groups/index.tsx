/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { getGroupsByTenantId } from "src/utility/api/tenants";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import { TrashCan } from "@carbon/react/icons";
import DeleteModal from "src/pages/tenants/detail/groups/DeleteModal";
import AssignGroupsModal from "src/pages/tenants/detail/groups/AssignGroupsModal";
import { isInternalGroupsEnabled } from "src/configuration";
import { useEnrichedGroups } from "src/components/global/useEnrichGroups";
import { GroupKeys } from "src/utility/api/groups";

type GroupsProps = {
  tenantId: string;
};

const Groups: FC<GroupsProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");

  const { groups, loading, success, reload } = useEnrichedGroups(
    getGroupsByTenantId,
    {
      tenantId,
    },
  );

  const isGroupsEmpty = !groups || groups.length === 0;

  const [assignGroups, assignGroupsModal] = useEntityModal(
    AssignGroupsModal,
    reload,
    {
      assignedGroups: groups,
    },
  );
  const openAssignModal = () => assignGroups({ id: tenantId });
  const [unassignGroup, unassignGroupModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      tenant: tenantId,
    },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadGroups")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isGroupsEmpty)
    return (
      <>
        <C3EmptyState
          heading={t("assignGroupsToTenant")}
          description={t("tenantMemberAccessDisclaimer")}
          button={{
            label: t("assignGroup"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutTenants"),
            href: "https://docs.camunda.io/",
          }}
        />
        {assignGroupsModal}
      </>
    );

  type GroupsListHeaders = {
    header: string;
    key: GroupKeys;
  }[];

  const groupsListHeaders: GroupsListHeaders = isInternalGroupsEnabled
    ? [
        { header: t("groupId"), key: "groupId" },
        { header: t("groupName"), key: "name" },
      ]
    : [{ header: t("groupId"), key: "groupId" }];

  return (
    <>
      <EntityList
        data={groups}
        headers={groupsListHeaders}
        sortProperty="groupId"
        loading={loading}
        addEntityLabel={t("assignGroup")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByGroupId")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignGroup,
          },
        ]}
      />
      {assignGroupsModal}
      {unassignGroupModal}
    </>
  );
};

export default Groups;
