/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { getGroupsByRoleId } from "src/utility/api/roles";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import AssignGroupsModal from "src/pages/roles/detail/groups/AssignGroupsModal";
import AssignGroupModal from "src/pages/roles/detail/groups/AssignGroupModal";
import DeleteModal from "src/pages/roles/detail/groups/DeleteModal";
import { docsUrl, isCamundaGroupsEnabled } from "src/configuration";
import { GroupKeys } from "src/utility/api/groups";
import { useEnrichedGroups } from "src/components/global/useEnrichGroups";

type GroupsProps = {
  roleId: string;
};

const Groups: FC<GroupsProps> = ({ roleId }) => {
  const { t } = useTranslate("roles");

  const { groups, loading, success, reload, paginationProps } =
    useEnrichedGroups(getGroupsByRoleId, {
      roleId,
    });

  const isGroupsEmpty = !groups || groups.length === 0;
  const [assignGroups, assignGroupsModal] = useEntityModal(
    isCamundaGroupsEnabled ? AssignGroupsModal : AssignGroupModal,
    reload,
    {
      assignedGroups: groups,
    },
  );
  const openAssignModal = () => assignGroups({ roleId });
  const [unassignGroup, unassignGroupModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      role: roleId,
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
          heading={t("assignGroupsToRole")}
          description={t("roleMemberAccessDisclaimer")}
          button={{
            label: t("assignGroup"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutRoles"),
            href: docsUrl,
          }}
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
            icon: TrashCan,
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
