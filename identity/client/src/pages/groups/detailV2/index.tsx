/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { OverflowMenu, OverflowMenuItem, Section, Stack } from "@carbon/react";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { groupQueries } from "src/utility/api/groups/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
// TODO: Replace with Tailwind classes.
import Flex from "src/components/layout/Flex";
// TODO: Replace with PageHeader during migration. See roles/detailV2/index.tsx for example.
import PageHeadline from "src/components/layoutV2/PageHeadline";
// TODO: Replace with V2 tabs. See roles/detailV2/index.tsx for example.
import Tabs from "src/components/tabs";
import { useEntityModal } from "src/components/modalV2";
import EditModal from "src/pages/groups/modalsV2/EditModal";
import DeleteModal from "src/pages/groups/modalsV2/DeleteModal";
// TODO: Remove during migration. See roles/detailV2/index.tsx for example.
import { Description } from "src/components/layout/DetailsPageDescription";
import Members from "src/pages/groups/detailV2/members";
import Roles from "src/pages/groups/detailV2/roles";
import MappingRules from "src/pages/groups/detailV2/mapping-rules";
import Clients from "src/pages/groups/detailV2/clients";

type DetailsProps = {
  isOIDC: boolean;
};

const Details: FC<DetailsProps> = ({ isOIDC }) => {
  const navigate = useNavigate();
  const { t } = useTranslate("groups");
  const { id = "", tab = "details" } = useParams<{
    id: string;
    tab: string;
  }>();

  const { data: group, isLoading: loading } = useQuery(groupQueries.detail(id));
  const [editGroup, editModal] = useEntityModal(EditModal, () => {});
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  if (!loading && !group) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap="2">
          <Breadcrumbs items={[{ href: "/groups", title: t("groups") }]} />
          {loading && !group ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {group && (
                <Stack gap="3">
                  <Stack orientation="horizontal" gap="1">
                    <PageHeadline>{group.name}</PageHeadline>
                    <OverflowMenu ariaLabel={t("openGroupContextMenu")}>
                      <OverflowMenuItem
                        itemText={t("edit")}
                        onClick={() => {
                          editGroup(group);
                        }}
                      />
                      <OverflowMenuItem
                        itemText={t("delete")}
                        isDelete
                        onClick={() => {
                          deleteGroup(group);
                        }}
                      />
                    </OverflowMenu>
                  </Stack>
                  <p>
                    {t("groupId")}: {group.groupId}
                  </p>
                  {group.description && (
                    <Description>
                      {t("description")}: {group.description}
                    </Description>
                  )}
                </Stack>
              )}
            </Flex>
          )}
        </Stack>
        {group && (
          <Section>
            <Tabs
              tabs={[
                {
                  key: "users",
                  label: t("users"),
                  content: <Members groupId={group.groupId} isOIDC={isOIDC} />,
                },
                {
                  key: "roles",
                  label: t("roles"),
                  content: <Roles groupId={group.groupId} />,
                },
                ...(isOIDC
                  ? [
                      {
                        key: "mapping-rules",
                        label: t("mappingRules"),
                        content: <MappingRules groupId={group.groupId} />,
                      },
                      {
                        key: "clients",
                        label: t("clients"),
                        content: <Clients groupId={group?.groupId} />,
                      },
                    ]
                  : []),
              ]}
              selectedTabKey={tab}
              path={`../${id}`}
            />
          </Section>
        )}
        {editModal}
        {deleteModal}
      </>
    </StackPage>
  );
};

export default Details;
