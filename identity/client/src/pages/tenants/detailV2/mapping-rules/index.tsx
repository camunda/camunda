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
import DeleteModal from "src/pages/tenants/detailV2/mapping-rules/DeleteModal";
import AssignMappingRulesModal from "src/pages/tenants/detailV2/mapping-rules/AssignMappingRulesModal.tsx";
import TabEmptyState from "src/components/layoutV2/TabEmptyState";

type MappingRulesProps = {
  tenantId: string;
};

const MappingRules: FC<MappingRulesProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");
  const noop = () => {};

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data: mappingRules,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery({
    ...tenantQueries.mappingRules(tenantId, pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((mappingRule) => ({
        ...mappingRule,
        id: mappingRule.mappingRuleId,
      })),
    }),
  });

  const isAssignedMappingRulesListEmpty =
    !mappingRules || mappingRules.items?.length === 0;

  const [assignMappingRules, assignMappingRulesModal] = useEntityModal(
    AssignMappingRulesModal,
    noop,
    {
      assignedMappingRules: mappingRules?.items || [],
    },
  );
  const openAssignModal = () => assignMappingRules({ id: tenantId });
  const [unassignMappingRule, unassignMappingRuleModal] = useEntityModal(
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
          resourceType: t("mappingRule").toLowerCase(),
        })}
        actionButton={{
          label: t("retry"),
          onClick: () => {
            void reload();
          },
        }}
      />
    );

  if (success && isAssignedMappingRulesListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"mappingRule"}
          parentResourceTypeTranslationKey={"tenant"}
          handleClick={openAssignModal}
          description={t("emptyStateTenantAccessDisclaimer")}
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
            icon: Trash2,
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
