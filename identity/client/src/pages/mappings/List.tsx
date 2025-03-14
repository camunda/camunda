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
import useModal from "src/components/modal/useModal";
import AddModal from "src/pages/mappings/modals/AddModal";
import { searchMapping } from "src/utility/api/mappings";

const List: FC = () => {
  const { t } = useTranslate();
  const {
    data: mappingSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchMapping);

  const [addMapping, addMappingModal] = useModal(AddModal, reload);
  const pageHeader = (
    <PageHeader
      title="Mappings"
      linkText="mappings"
      linkUrl="/concepts/mappings/"
    />
  );

  if (success && !mappingSearchResults?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("You don’t have any mappings yet")}
          description={t("Mapping of JWT token")}
          button={{
            label: t("Create a mapping"),
            onClick: addMapping,
            icon: Add,
          }}
          link={{
            href: documentationHref("/concepts/mapping/", ""),
            label: t("Learn more about mapping"),
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
          { header: t("Mapping ID"), key: "mappingKey" },
          { header: t("Mapping name"), key: "name" },
          { header: t("Claim name"), key: "claimName" },
          { header: t("Claim value"), key: "claimValue" },
        ]}
        sortProperty="claimName"
        addEntityLabel={t("Create mapping")}
        onAddEntity={addMapping}
        loading={loading}
        menuItems={[
          {
            label: t("Edit"),
            icon: Edit,
            onClick: () => {},
          },
          {
            label: t("Delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: () => {},
          },
        ]}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("The list of mappings could not be loaded.")}
          actionButton={{ label: t("Retry"), onClick: reload }}
        />
      )}
      {addMappingModal}
    </Page>
  );
};

export default List;
