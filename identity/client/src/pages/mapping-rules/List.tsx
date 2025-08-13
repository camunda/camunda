/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Edit, TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import { AddModal } from "src/pages/mapping-rules/modals/add-modal";
import { searchMappingRule } from "src/utility/api/mapping-rules";
import DeleteModal from "src/pages/mapping-rules/modals/DeleteModal";
import EditModal from "src/pages/mapping-rules/modals/EditModal";
import PageEmptyState from "src/components/layout/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("mappingRules");
  const RESOURCE_TYPE_STRING = t("mappingRule").toLowerCase();
  const {
    data: mappingRuleSearchResults,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchMappingRule);

  const [addMappingRule, addMappingRuleModal] = useModal(AddModal, reload);
  const [editMappingRule, editMappingRuleModal] = useEntityModal(
    EditModal,
    reload,
  );
  const [deleteMappingRule, deleteMappingRuleModal] = useEntityModal(
    DeleteModal,
    reload,
  );

  const shouldShowEmptyState =
    success && !search && !mappingRuleSearchResults?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("mappingRules")}
      linkText={t("mappingRules").toLowerCase()}
      docsLinkPath=""
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceType={RESOURCE_TYPE_STRING}
          docsLinkPath=""
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
        data={
          mappingRuleSearchResults == null ? [] : mappingRuleSearchResults.items
        }
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
            icon: Edit,
            onClick: editMappingRule,
          },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteMappingRule,
          },
        ]}
        searchPlaceholder={t("searchByMappingRuleId")}
        searchKey="mappingRuleId"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("loadMappingRulesError")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addMappingRuleModal}
      {deleteMappingRuleModal}
      {editMappingRuleModal}
    </Page>
  );
};

export default List;
