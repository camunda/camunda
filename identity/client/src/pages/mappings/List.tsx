/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import { Stack } from "@carbon/react";
import { spacing06 } from "@carbon/elements";
import { Edit, TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageTitle } from "src/components/layout/Page";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import {
  documentationHref,
  DocumentationLink,
} from "src/components/documentation";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal from "src/components/modal/useModal";
import AddModal from "src/pages/mappings/modals/AddModal";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { searchMapping } from "src/utility/api/mappings";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const [, setSearch] = useState("");
  const {
    data: mappingSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchMapping);

  const [addMapping, addMappingModal] = useModal(AddModal, reload);

  if (success && !mappingSearchResults?.items.length) {
    return (
      <Page>
        <Stack gap={spacing06}>
          <PageTitle>
            <Translate>Mappings</Translate>
          </PageTitle>
          <C3EmptyState
            heading={t("You don’t have any mappings yet")}
            description={t("Mapping of JWT token")}
            button={{
              label: t("Create a mapping"),
              onClick: addMapping,
            }}
            link={{
              href: documentationHref("/concepts/mapping/", ""),
              label: t("Learn more about mapping"),
            }}
          />
        </Stack>
        {addMappingModal}
      </Page>
    );
  }

  return (
    <Page>
      <EntityList
        title={t("Mappings")}
        data={mappingSearchResults == null ? [] : mappingSearchResults.items}
        headers={[
          { header: t("Mapping Key"), key: "mappingKey" },
          { header: t("Claim Name"), key: "claimName" },
          { header: t("Claim Value"), key: "claimValue" },
        ]}
        sortProperty="claimName"
        addEntityLabel={t("Create mapping")}
        onAddEntity={addMapping}
        onSearch={setSearch}
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
      {success && (
        <DocumentationDescription>
          <Translate>Learn more about mapping in our</Translate>{" "}
          <DocumentationLink path="/concepts/mapping/" />.
        </DocumentationDescription>
      )}
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
