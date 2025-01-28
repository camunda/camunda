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
import {
  searchAuthorization,
  NewAuthorization,
} from "src/utility/api/authorizations";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/authorizations/modals/AddModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";
import { TabsVertical, Tab, TabPanels } from "@carbon/react";
import { CustomTabListVertical, CustomTabPanel, TabsTitle } from "./components";
import { ResourceType } from "../users/detail/authorization/PatchModal";

// @TODO: remove mocked data
const authorizationSearchResults = {
  items: [
    {
      key: "key",
      ownerType: "ownerType",
      ownerId: "resourceId",
      resourceId: "resourceId",
      resourceType: "resourceType",
      permissions: ["permission1", "permission2"],
    },
    {
      key: "key2",
      ownerType: "ownerType",
      ownerId: "ownerId2",
      resourceId: "resourceId2",
      resourceType: "resourceType",
      permissions: ["permission1", "permission2"],
    },
    {
      key: "key3",
      ownerType: "ownerType",
      ownerId: "ownerI3",
      resourceId: "resourceId3",
      resourceType: "resourceType",
      permissions: ["permission1", "permission2"],
    },
  ],
  page: {
    totalItems: 3,
    firstSortValues: [{}],
    lastSortValues: [{}],
  },
};

const List: FC = () => {
  const { t } = useTranslate("authorizations");
  const navigate = useNavigate();
  const [, setSearch] = useState("");
  const { data, loading, reload, success } = useApi(searchAuthorization); // @TODO: CHANGE API USED TO AUTHORIZATIONS
  console.log("data", data);

  const [addAuthorization, addAuthorizationModal] = useModal(AddModal, reload); // @TODO: change modal used to be add authorization
  const [, deleteAuthorizationModal] = useEntityModal(DeleteModal, reload); // @TODO: change used modal to delete authorization

  const showDetails = ({ authorizationKey }: NewAuthorization) =>
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

  const authorizationTabs = Object.keys(ResourceType).map((type) => t(type));

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
          {authorizationTabs.map((tab) => (
            <Tab key={tab}>{tab}</Tab>
          ))}
        </CustomTabListVertical>
        <TabPanels>
          {authorizationTabs.map((tab) => (
            <CustomTabPanel key={tab}>
              <EntityList<NewAuthorization>
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
                hasTableSearch={false}
              />
            </CustomTabPanel>
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
