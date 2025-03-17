/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { Edit, TrashCan, Add } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { documentationHref } from "src/components/documentation";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/mappings/modals/AddModal";
import { searchMapping } from "src/utility/api/mappings";
import DeleteModal from "src/pages/mappings/modals/DeleteModal";
import EditModal from "src/pages/mappings/modals/EditModal";
import { IS_UPDATE_MAPPINGS_SUPPORTED } from "src/feature-flags";

const List: FC = () => {
  const { t } = useTranslate("mappingRules");
  const {
    data: mappingSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchMapping);

  const [addMapping, addMappingModal] = useModal(AddModal, reload);
  const [editMapping, editMappingModal] = useEntityModal(EditModal, reload);
  const [deleteMapping, deleteMappingModal] = useEntityModal(
    DeleteModal,
    reload,
  );

  const pageHeader = (
    <PageHeader
      title={t("mappings")}
      linkText={t("mappings")}
      linkUrl="/concepts/mappings/"
    />
  );

  if (success && !mappingSearchResults?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("noMappings")}
          description={t("mappingJWTToken")}
          button={{
            label: t("createMapping"),
            onClick: addMapping,
            icon: Add,
          }}
          link={{
            href: documentationHref("/concepts/mapping/", ""),
            label: t("learnMoreMapping"),
          }}
        />
        {addMappingModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={mappingSearchResults == null ? [] : mappingSearchResults.items}
        headers={[
          { header: t("mappingId"), key: "mappingKey" },
          { header: t("mappingName"), key: "name" },
          { header: t("claimName"), key: "claimName" },
          { header: t("claimValue"), key: "claimValue" },
        ]}
        sortProperty="claimName"
        addEntityLabel={t("createMapping")}
        onAddEntity={addMapping}
        loading={loading}
        menuItems={[
          {
            label: t("edit"),
            icon: Edit,
            onClick: editMapping,
            hidden: !IS_UPDATE_MAPPINGS_SUPPORTED,
          },
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: deleteMapping,
          },
        ]}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("loadMappingsError")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addMappingModal}
      {deleteMappingModal}
      {editMappingModal}
    </Page>
  );
};

export default List;
