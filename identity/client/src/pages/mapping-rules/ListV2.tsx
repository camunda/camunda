/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Pencil, Trash2 } from "lucide-react";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { useQuery } from "@tanstack/react-query";
import { mappingRuleQueries } from "src/utility/api/mapping-rules/queries";
import Page, { PageHeader } from "src/components/layoutV2/Page";
import EntityList from "src/components/entityListV2";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import useModal, { useEntityModal } from "src/components/modalV2/useModal";
import AddModal from "src/pages/mapping-rules/modalsV2/AddModal";
import DeleteModal from "src/pages/mapping-rules/modalsV2/DeleteModal";
import EditModal from "src/pages/mapping-rules/modalsV2/EditModal";
import PageEmptyState from "src/components/layoutV2/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("mappingRules");
  const noop = () => {};

  const { pageParams, page, search, ...paginationCallbacks } = usePagination();
  const {
    data: mappingRuleSearchResults,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery({
    ...mappingRuleQueries.search(pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((rule) => ({ ...rule, id: rule.mappingRuleId })),
    }),
  });

  const [addMappingRule, addMappingRuleModal] = useModal(AddModal, noop);
  const [editMappingRule, editMappingRuleModal] = useEntityModal(
    EditModal,
    noop,
  );
  const [deleteMappingRule, deleteMappingRuleModal] = useEntityModal(
    DeleteModal,
    noop,
  );

  const shouldShowEmptyState =
    success && !search && mappingRuleSearchResults.items.length === 0;

  const pageHeader = (
    <PageHeader
      title={t("mappingRules")}
      linkText={t("mappingRules").toLowerCase()}
      docsLinkPath="/components/admin/mapping-rules/"
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceTypeTranslationKey={"mappingRule"}
          docsLinkPath="/components/admin/mapping-rules/"
          handleClick={addMappingRule}
        />
        {addMappingRuleModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={mappingRuleSearchResults?.items}
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
        addEntityLabel={t("createMappingRule")}
        onAddEntity={addMappingRule}
        loading={loading}
        menuItems={[
          {
            label: t("edit"),
            icon: Pencil,
            onClick: editMappingRule,
          },
          {
            label: t("delete"),
            icon: Trash2,
            isDangerous: true,
            onClick: deleteMappingRule,
          },
        ]}
        searchPlaceholder={t("searchByMappingRuleId")}
        searchKey="mappingRuleId"
        page={{ ...page, ...mappingRuleSearchResults?.page }}
        {...paginationCallbacks}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("loadMappingRulesError")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
      {addMappingRuleModal}
      {deleteMappingRuleModal}
      {editMappingRuleModal}
    </Page>
  );
};

export default List;
