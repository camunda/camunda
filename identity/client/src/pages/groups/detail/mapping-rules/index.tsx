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
import { getMappingRulesByGroupId } from "src/utility/api/groups";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/groups/detail/mapping-rules/DeleteModal";
import AssignMappingRulesModal from "src/pages/groups/detail/mapping-rules/AssignMappingRulesModal.tsx";
import TabEmptyState from "src/components/layout/TabEmptyState";

type MappingRulesProps = {
  groupId: string;
};

const MappingRules: FC<MappingRulesProps> = ({ groupId }) => {
  const { t } = useTranslate("groups");
  const CHILD_RESOURCE_TYPE_STRING = t("mappingRule").toLowerCase();
  const PARENT_RESOURCE_TYPE_STRING = t("group").toLowerCase();

  const {
    data: mappingRules,
    loading,
    success,
    reload,
    ...paginationProps
  } = usePaginatedApi(getMappingRulesByGroupId, {
    groupId: groupId,
  });

  const isMappingRulesListEmpty =
    !mappingRules || mappingRules.items?.length === 0;

  const [assignMappingRules, assignMappingRulesModal] = useEntityModal(
    AssignMappingRulesModal,
    reload,
    {
      assignedMappingRules: mappingRules?.items || [],
    },
  );
  const openAssignModal = () => assignMappingRules({ id: groupId });
  const [unassignMappingRule, unassignMappingRuleModal] = useEntityModal(
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

  if (success && isMappingRulesListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceType={CHILD_RESOURCE_TYPE_STRING}
          parentResourceType={PARENT_RESOURCE_TYPE_STRING}
          handleClick={openAssignModal}
          docsLinkPath=""
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
