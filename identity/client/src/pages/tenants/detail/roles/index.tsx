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
import { getRolesByTenantId } from "src/utility/api/tenants";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/tenants/detail/roles/DeleteModal";
import AssignRolesModal from "src/pages/tenants/detail/roles/AssignRolesModal";
import TabEmptyState from "src/components/layout/TabEmptyState";

type RolesProps = {
  tenantId: string;
};

const Roles: FC<RolesProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");
  const CHILD_RESOURCE_TYPE_STRING = t("role").toLowerCase();
  const PARENT_RESOURCE_TYPE_STRING = t("tenant").toLowerCase();

  const {
    data: roles,
    loading,
    success,
    reload,
    ...paginationProps
  } = usePaginatedApi(getRolesByTenantId, {
    tenantId: tenantId,
  });

  const isAssignedRolesListEmpty = !roles || roles.items?.length === 0;

  const [assignRoles, assignRolesModal] = useEntityModal(
    AssignRolesModal,
    reload,
    {
      assignedRoles: roles?.items || [],
    },
  );
  const openAssignModal = () => assignRoles({ id: tenantId });
  const [unassignRole, unassignRoleModal] = useEntityModal(
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
        description={t("unableToLoadResource", {
          resourceType: CHILD_RESOURCE_TYPE_STRING,
        })}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isAssignedRolesListEmpty)
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
          { header: t("roleId"), key: "roleId", isSortable: true },
          { header: t("roleName"), key: "name", isSortable: true },
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
