/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Trash2 } from "lucide-react";
import useTranslate from "src/utility/localization";
import { getGroupsByTenantId } from "src/utility/api/tenants";
import EntityList from "src/components/entityListV2";
import { useEntityModal } from "src/components/modalV2";
import { ErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import AssignGroupsModal from "src/pages/tenants/detailV2/groups/AssignGroupsModal";
import AssignGroupModal from "src/pages/tenants/detailV2/groups/AssignGroupModal";
import DeleteModal from "src/pages/tenants/detailV2/groups/DeleteModal";
import { GroupKeys } from "src/utility/api/groups";
import { useEnrichedGroups } from "src/components/global/useEnrichGroups";
import TabEmptyState from "src/components/layoutV2/TabEmptyState";

type GroupsProps = {
  tenantId: string;
  isCamundaGroupsEnabled: boolean;
};

const Groups: FC<GroupsProps> = ({ tenantId, isCamundaGroupsEnabled }) => {
  const { t } = useTranslate("tenants");

  const { groups, loading, success, reload, paginationProps } =
    useEnrichedGroups(
      "tenants",
      getGroupsByTenantId,
      {
        tenantId,
      },
      isCamundaGroupsEnabled,
    );
  const noop = () => {};

  const isGroupsEmpty = !groups || groups.length === 0;
  const [assignGroups, assignGroupsModal] = useEntityModal(
    isCamundaGroupsEnabled ? AssignGroupsModal : AssignGroupModal,
    noop,
    {
      assignedGroups: groups,
    },
  );
  const openAssignModal = () => assignGroups({ tenantId });
  const [unassignGroup, unassignGroupModal] = useEntityModal(
    DeleteModal,
    noop,
    {
      tenant: tenantId,
    },
  );

  if (!loading && !success)
    return (
      <ErrorInlineNotification
        title={t("somethingsWrong")}
        subtitle={t("unableToLoadResource", {
          resourceType: t("group").toLowerCase(),
        })}
        actionButton={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isGroupsEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"group"}
          parentResourceTypeTranslationKey={"tenant"}
          handleClick={openAssignModal}
          description={t("emptyStateTenantAccessDisclaimer")}
          docsLinkPath="/components/admin/group/"
        />
        {assignGroupsModal}
      </>
    );

  type GroupsListHeaders = {
    header: string;
    key: GroupKeys;
    isSortable?: boolean;
  }[];

  const groupsListHeaders: GroupsListHeaders = isCamundaGroupsEnabled
    ? [
        { header: t("groupId"), key: "groupId", isSortable: true },
        { header: t("groupName"), key: "name" },
      ]
    : [{ header: t("groupId"), key: "groupId", isSortable: true }];

  return (
    <>
      <EntityList
        data={groups}
        headers={groupsListHeaders}
        loading={loading}
        addEntityLabel={t("assignGroup")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByGroupId")}
        menuItems={[
          {
            label: t("remove"),
            icon: Trash2,
            isDangerous: true,
            onClick: unassignGroup,
          },
        ]}
        {...paginationProps}
      />
      {assignGroupsModal}
      {unassignGroupModal}
    </>
  );
};

export default Groups;
