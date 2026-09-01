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
import { useQuery } from "@tanstack/react-query";
import { usePagination } from "src/utility/api";
import { tenantQueries } from "src/utility/api/tenants/queries";
import EntityList from "src/components/entityListV2";
import { useEntityModal } from "src/components/modalV2";
import { ErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import DeleteModal from "src/pages/tenants/detailV2/roles/DeleteModal";
import AssignRolesModal from "src/pages/tenants/detailV2/roles/AssignRolesModal";
import TabEmptyState from "src/components/layoutV2/TabEmptyState";

type RolesProps = {
  tenantId: string;
};

const Roles: FC<RolesProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");
  const noop = () => {};

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data: roles,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery({
    ...tenantQueries.roles(tenantId, pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((role) => ({ ...role, id: role.roleId })),
    }),
  });

  const isAssignedRolesListEmpty = !roles || roles.items?.length === 0;

  const [assignRoles, assignRolesModal] = useEntityModal(
    AssignRolesModal,
    noop,
    {
      assignedRoles: roles?.items || [],
    },
  );
  const openAssignModal = () => assignRoles({ id: tenantId });
  const [unassignRole, unassignRoleModal] = useEntityModal(DeleteModal, noop, {
    tenant: tenantId,
  });

  if (!loading && !success)
    return (
      <ErrorInlineNotification
        title={t("somethingsWrong")}
        subtitle={t("unableToLoadResource", {
          resourceType: t("role").toLowerCase(),
        })}
        actionButton={{
          label: t("retry"),
          onClick: () => {
            void reload();
          },
        }}
      />
    );

  if (success && isAssignedRolesListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"role"}
          parentResourceTypeTranslationKey={"tenant"}
          handleClick={openAssignModal}
          description={t("emptyStateTenantAccessDisclaimer")}
          docsLinkPath="/components/admin/role/"
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
            icon: Trash2,
            isDangerous: true,
            onClick: unassignRole,
          },
        ]}
        page={{ ...page, ...roles?.page }}
        {...paginationCallbacks}
      />
      {assignRolesModal}
      {unassignRoleModal}
    </>
  );
};

export default Roles;
