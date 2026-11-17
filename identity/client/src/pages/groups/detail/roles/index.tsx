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
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { groupQueries } from "src/utility/api/groups/queries";
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
  const noop = () => {};

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data: roles,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery(groupQueries.roles(groupId, pageParams));

  const isRolesListEmpty = !roles || roles.items?.length === 0;

  const [assignRoles, assignRolesModal] = useEntityModal(
    AssignRolesModal,
    noop,
    {
      assignedRoles: roles?.items || [],
    },
  );
  const openAssignModal = () => assignRoles({ id: groupId });
  const [unassignRole, unassignRoleModal] = useEntityModal(DeleteModal, noop, {
    groupId,
  });

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadResource", {
          resourceType: t("role").toLowerCase(),
        })}
        button={{
          label: t("retry"),
          onClick: () => {
            void reload();
          },
        }}
      />
    );

  if (success && isRolesListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"role"}
          parentResourceTypeTranslationKey={"group"}
          handleClick={openAssignModal}
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
            icon: TrashCan,
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
