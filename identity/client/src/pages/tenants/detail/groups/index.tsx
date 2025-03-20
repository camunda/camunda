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
import { useApi } from "src/utility/api/hooks";
import { getGroupsByTenantId } from "src/utility/api/tenants";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import { TrashCan } from "@carbon/react/icons";
import DeleteModal from "src/pages/tenants/detail/groups/DeleteModal";
import AssignGroupsModal from "src/pages/tenants/detail/groups/AssignGroupsModal";

type GroupsProps = {
  tenantId: string;
};

const Groups: FC<GroupsProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");

  const {
    data: groups,
    loading,
    success,
    reload,
  } = useApi(getGroupsByTenantId, {
    tenantId: tenantId,
  });

  const isGroupsEmpty = !groups || groups.items?.length === 0;

  const [assignGroups, assignGroupsModal] = useEntityModal(
    AssignGroupsModal,
    reload,
    {
      assignedGroups: groups?.items || [],
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
            href: `/identity/concepts/access-control/tenants`,
          }}
        />
        {assignGroupsModal}
      </>
    );

  return (
    <>
      <EntityList
        data={groups?.items}
        headers={[
          { header: t("groupId"), key: "groupKey" },
          { header: t("groupName"), key: "name" },
        ]}
        sortProperty="groupKey"
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
