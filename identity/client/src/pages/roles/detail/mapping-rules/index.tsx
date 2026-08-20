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
import { roleQueries } from "src/utility/api/roles/queries";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/roles/detail/mapping-rules/DeleteModal";
import AssignMappingRulesModal from "src/pages/roles/detail/mapping-rules/AssignMappingRulesModal.tsx";
import TabEmptyState from "src/components/layout/TabEmptyState";

type MappingRulesProps = {
  roleId: string;
};

const MappingRules: FC<MappingRulesProps> = ({ roleId }) => {
  const { t } = useTranslate("roles");
  const noop = () => {};

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data: mappingRules,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery(roleQueries.mappingRules(roleId, pageParams));

  const isMappingRulesListEmpty =
    !mappingRules || mappingRules.items?.length === 0;

  const [assignMappingRules, assignMappingRulesModal] = useEntityModal(
    AssignMappingRulesModal,
    noop,
    {
      assignedMappingRules: mappingRules?.items || [],
    },
  );
  const openAssignModal = () => assignMappingRules({ id: roleId });
  const [unassignMappingRule, unassignMappingRuleModal] = useEntityModal(
    DeleteModal,
    noop,
    {
      roleId,
    },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadResource", {
          resourceType: t("mappingRule").toLowerCase(),
        })}
        button={{
          label: t("retry"),
          onClick: () => {
            void reload();
          },
        }}
      />
    );

  if (success && isMappingRulesListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"mappingRule"}
          parentResourceTypeTranslationKey={"role"}
          handleClick={openAssignModal}
          docsLinkPath="/components/admin/mapping-rules/"
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
        page={{ ...page, ...mappingRules?.page }}
        {...paginationCallbacks}
      />
      {assignMappingRulesModal}
      {unassignMappingRuleModal}
    </>
  );
};

export default MappingRules;
