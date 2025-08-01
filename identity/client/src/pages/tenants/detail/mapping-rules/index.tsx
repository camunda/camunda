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
import { getMappingRulesByTenantId } from "src/utility/api/tenants";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/tenants/detail/mapping-rules/DeleteModal";
import AssignMappingRulesModal from "src/pages/tenants/detail/mapping-rules/AssignMappingRulesModal.tsx";

type MappingRulesProps = {
  tenantId: string;
};

const MappingRules: FC<MappingRulesProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");

  const {
    data: mappingRules,
    loading,
    success,
    reload,
    ...paginationProps
  } = usePaginatedApi(getMappingRulesByTenantId, {
    tenantId: tenantId,
  });

  const isAssignedMappingRulesListEmpty =
    !mappingRules || mappingRules.items?.length === 0;

  const [assignMappingRules, assignMappingRulesModal] = useEntityModal(
    AssignMappingRulesModal,
    reload,
    {
      assignedMappingRules: mappingRules?.items || [],
    },
  );
  const openAssignModal = () => assignMappingRules({ id: tenantId });
  const [unassignMappingRule, unassignMappingRuleModal] = useEntityModal(
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
        description={t("unableToLoadMappingRules")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isAssignedMappingRulesListEmpty)
    return (
      <>
        <C3EmptyState
          heading={t("assignMappingRulesToTenant")}
          description={t("tenantMemberAccessDisclaimer")}
          button={{
            label: t("assignMappingRule"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutTenants"),
            href: "https://docs.camunda.io/",
          }}
        />
        {assignMappingRulesModal}
      </>
    );

  return (
    <>
      <EntityList
        data={mappingRules?.items}
        headers={[
          {
            header: t("mappingRuleId"),
            key: "mappingRuleId",
            isSortable: true,
          },
          { header: t("mappingRuleName"), key: "name", isSortable: true },
          { header: t("claimName"), key: "claimName", isSortable: true },
          { header: t("claimValue"), key: "claimValue", isSortable: true },
        ]}
        loading={loading}
        addEntityLabel={t("assignMappingRule")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByMappingRuleId")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignMappingRule,
          },
        ]}
        {...paginationProps}
      />
      {assignMappingRulesModal}
      {unassignMappingRuleModal}
    </>
  );
};

export default MappingRules;
