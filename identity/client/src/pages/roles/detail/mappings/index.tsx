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
import { useApi } from "src/utility/api/hooks";
import { getMappingsByRoleId } from "src/utility/api/roles";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/roles/detail/mappings/DeleteModal";
import AssignMappingsModal from "src/pages/roles/detail/mappings/AssignMappingsModal";

type MappingsProps = {
  roleId: string;
};

const Mappings: FC<MappingsProps> = ({ roleId }) => {
  const { t } = useTranslate("roles");

  const {
    data: mappings,
    loading,
    success,
    reload,
  } = useApi(getMappingsByRoleId, {
    roleId: roleId,
  });

  const isMappingsListEmpty = !mappings || mappings.items?.length === 0;

  const [assignMappings, assignMappingsModal] = useEntityModal(
    AssignMappingsModal,
    reload,
    {
      assignedMappings: mappings?.items || [],
    },
  );
  const openAssignModal = () => assignMappings({ id: roleId });
  const [unassignMapping, unassignMappingModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      roleId,
    },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadMappings")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isMappingsListEmpty)
    return (
      <>
        <C3EmptyState
          heading={t("assignMappingsToRole")}
          description={t("accessDisclaimer")}
          button={{
            label: t("assignMapping"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutRoles"),
            href: `/identity/concepts/access-control/roles`,
          }}
        />
        {assignMappingsModal}
      </>
    );

  return (
    <>
      <EntityList
        data={mappings?.items}
        headers={[
          { header: t("mappingId"), key: "id" },
          { header: t("mappingName"), key: "name" },
          { header: t("claimName"), key: "claimName" },
          { header: t("claimValue"), key: "claimValue" },
        ]}
        sortProperty="id"
        loading={loading}
        addEntityLabel={t("assignMapping")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByMappingId")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignMapping,
          },
        ]}
      />
      {assignMappingsModal}
      {unassignMappingModal}
    </>
  );
};

export default Mappings;
