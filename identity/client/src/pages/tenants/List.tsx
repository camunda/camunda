/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
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
import { searchTenant, Tenant } from "src/utility/api/tenants";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal from "src/components/modal/useModal";
import AddModal from "src/pages/tenants/modals/AddModal";
import { C3EmptyState } from "@camunda/camunda-composite-components";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");
  const {
    data: tenantSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchTenant);
  const [addTenant, addTenantModal] = useModal(AddModal, reload);

  const showDetails = ({ tenantId }: Tenant) => navigate(`${tenantId}`);

  if (success && !tenantSearchResults?.items.length) {
    return (
        <Page>
          <PageTitle>
            <Translate>Tenants</Translate>
          </PageTitle>
          <C3EmptyState
              heading={t("You don’t have any tenants yet")}
              description={t(
                  "Create isolated environments with their own assigned users, groups, and applications."
              )}
              button={{
                label: t("Create a tenant"),
                onClick: addTenant,
              }}
              link={{
                href: documentationHref("/concepts/multi-tenancy/", ""),
                label: t("Learn more about tenants"),
              }}
          />
          {addTenantModal}
        </Page>
    );
  }

  return (
      <Page>
        <EntityList
            title={t("Tenants")}
            data={tenantSearchResults == null ? [] : tenantSearchResults.items}
            headers={[
              { header: t("Name"), key: "name" },
              { header: t("Tenant ID"), key: "tenantId" },
            ]}
            sortProperty="name"
            onEntityClick={showDetails}
            addEntityLabel={t("Create tenant")}
            onAddEntity={addTenant}
            onSearch={setSearch}
            loading={loading}
        />
        {success && (
            <DocumentationDescription>
              <Translate>Learn more about tenants in our</Translate>{" "}
              <DocumentationLink path="/concepts/multi-tenancy/" />.
            </DocumentationDescription>
        )}
        {!loading && !success && (
            <TranslatedErrorInlineNotification
                title={t("The list of tenants could not be loaded.")}
                actionButton={{ label: t("Retry"), onClick: reload }}
            />
        )}
        {addTenantModal}
      </Page>
  );
};

export default List;
