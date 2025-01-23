/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import { useNavigate } from "react-router";
import { TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";

import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { searchTenant } from "src/utility/api/tenants";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/tenants/modals/AddModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";
import { TabsVertical } from "@carbon/react";
import { Tab } from "@carbon/react";
import { TabPanels } from "@carbon/react";
import { TabPanel } from "@carbon/react";
import { CustomTabListVertical, TabsTitle } from "./components";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Authorization = any; // @TODO: REMOVE MOCK TYPE

const List: FC = () => {
  const { t } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");
  const {
    data: authorizationSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchTenant); // @TODO: CHANGE API USED TO AUTHORIZATIONS

  // @TODO: remove - empty state test
  // const authorizationSearchResults = { items: [] };

  const [addAuthorization, addAuthorizationModal] = useModal(AddModal, reload); // @TODO: change modal used to be add authorization
  const [, deleteAuthorizationModal] = useEntityModal(DeleteModal, reload); // @TODO: change used modal to delete authorization

  const showDetails = ({ authorizationKey }: Authorization) =>
    navigate(`${authorizationKey}`);

  if (success && !authorizationSearchResults?.items.length) {
    return (
      <Page>
        <PageHeader
          title="Authorizations"
          linkText="authorizations"
          linkUrl="/concepts/authorizations/"
          largeBottomMargin={true}
        />
        <C3EmptyState
          heading={t("You don’t have any authorizations yet")}
          description={t(
            "Create isolated environments with their own assigned users, groups, and applications.",
          )}
          button={{
            label: t("Create an authorization"),
            onClick: addAuthorization,
          }}
        />
        {addAuthorizationModal}
      </Page>
    );
  }

  const authorizationTypes = [
    "Application",
    "Authorization",
    "Batch",
    "Decision definition",
    "Decision requirements definition",
    "Deployments",
    "Groups",
    "Mapping rules",
    "Message",
    "Process definition ",
    "Roles",
    "System",
    "Tenants",
    "Users",
  ];

  return (
    <Page>
      <PageHeader
        title="Authorizations"
        linkText="authorizations"
        linkUrl="/concepts/authorizations/"
        largeBottomMargin={true}
      />
      <TabsTitle>Authorization type</TabsTitle>
      <TabsVertical>
        <CustomTabListVertical aria-label="Authorization type">
          {authorizationTypes.map((tab) => (
            <Tab>{tab}</Tab>
          ))}
        </CustomTabListVertical>
        <TabPanels>
          {authorizationTypes.map((tab) => (
            <TabPanel>
              <EntityList<Authorization>
                // @TODO: fix filter options showing up when unselected
                // filter={{
                //   title: "Filter",
                //   options: [{ id: "aa", label: "bb" }],
                //   callback: (Authorizations, selectedItems) => {
                //     console.log(selectedItems);
                //     return true;
                //   },
                // }}
                title={t(tab)}
                data={
                  !authorizationSearchResults
                    ? []
                    : authorizationSearchResults.items
                } // Determines D type of Entity List --- @TODO: remove comment
                headers={[
                  { header: t("Owner type"), key: "ownerType" },
                  { header: t("Owner ID"), key: "ownerId" },
                  { header: t("Resource ID"), key: "resourceId" },
                  { header: t("Permissions"), key: "permissions" },
                ]}
                sortProperty="name"
                onEntityClick={showDetails}
                addEntityLabel={t("Create authorization")}
                onAddEntity={addAuthorization}
                onSearch={setSearch}
                loading={loading}
                menuItems={[
                  {
                    label: t("Delete"),
                    icon: TrashCan,
                    isDangerous: true,
                    onClick: (tenant) => () => {
                      console.log(tenant);
                    },
                    // deleteAuthorization(tenant), // @TODO: add back deleteAuthorization (type issue with menuItems)
                  },
                ]}
              />
            </TabPanel>
          ))}
        </TabPanels>
      </TabsVertical>
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("The list of authorizations could not be loaded.")}
          actionButton={{ label: t("Retry"), onClick: reload }}
        />
      )}
      {addAuthorizationModal}
      {deleteAuthorizationModal}
    </Page>
  );
};

export default List;
