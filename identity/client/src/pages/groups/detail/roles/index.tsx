/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import { searchRolesByGroupId } from "src/utility/api/groups";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/groups/detail/roles/DeleteModal";
import AssignRolesModal from "src/pages/groups/detail/roles/AssignRolesModal";
import TabEmptyState from "src/components/layout/TabEmptyState";

type RolesProps = {
  groupId: string;
};

const Roles: FC<RolesProps> = ({ groupId }) => {
  const { t } = useTranslate("groups");
  const CHILD_RESOURCE_TYPE_STRING = t("role").toLowerCase();
  const PARENT_RESOURCE_TYPE_STRING = t("group").toLowerCase();

  const {
    data: roles,
    loading,
    success,
    reload,
    ...paginationProps
  } = usePaginatedApi(searchRolesByGroupId, {
    groupId: groupId,
  });

  const isRolesListEmpty = !roles || roles.items?.length === 0;

  const [assignRoles, assignRolesModal] = useEntityModal(
    AssignRolesModal,
    reload,
    {
      assignedRoles: roles?.items || [],
    },
  );
  const openAssignModal = () => assignRoles({ id: groupId });
  const [unassignRole, unassignRoleModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      groupId,
    },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadResource", {
          resourceType: CHILD_RESOURCE_TYPE_STRING,
        })}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isRolesListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceType={CHILD_RESOURCE_TYPE_STRING}
          parentResourceType={PARENT_RESOURCE_TYPE_STRING}
          handleClick={openAssignModal}
          docsLinkPath=""
        />
        {assignRolesModal}
      </>
    );

  return (
    <>
      <EntityList
        data={roles?.items}
        headers={[
          { header: t("roleId"), key: "roleId" },
          { header: t("roleName"), key: "name" },
        ]}
        loading={loading}
        addEntityLabel={t("assignRole")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByRoleId")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignRole,
          },
        ]}
        {...paginationProps}
      />
      {assignRolesModal}
      {unassignRoleModal}
    </>
  );
};

export default Roles;
