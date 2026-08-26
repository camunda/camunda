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
import { roleQueries } from "src/utility/api/roles/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
// TODO: Replace with Tailwind
import Flex from "src/components/layout/Flex";
import PageHeadline from "src/components/layoutV2/PageHeadline";
// TODO: Overhaul to work with `PageHeader`. Implementation reference users/detailV2/index.tsx for example.
import { Tabs, TabsContent, TabsHeaderList } from "src/components/tabsV2";
import { useEntityModal } from "src/components/modalV2";
import DeleteModal from "src/pages/roles/modalsV2/DeleteModal";
import EditModal from "src/pages/roles/modalsV2/EditModal";
// TODO: Use `PageHeader` description directly?
import { Description } from "src/components/layoutV2/DetailsPageDescription";
import Members from "src/pages/roles/detailV2/members";
import Groups from "src/pages/roles/detailV2/groups";
import MappingRules from "src/pages/roles/detailV2/mapping-rules";
import Clients from "src/pages/roles/detailV2/clients";

type DetailsProps = {
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  defaultRoleIds: string[];
};

const Details: FC<DetailsProps> = ({
  isOIDC,
  isCamundaGroupsEnabled,
  defaultRoleIds,
}) => {
  const navigate = useNavigate();
  const { t } = useTranslate("roles");
  const { id = "", tab = "details" } = useParams<{
    id: string;
    tab: string;
  }>();

  const { data: role, isLoading: loading } = useQuery(roleQueries.detail(id));

  const [editRole, editModal] = useEntityModal(EditModal, () => {});
  const [deleteRole, deleteModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  if (!loading && !role) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap="2">
          <Breadcrumbs items={[{ href: "/roles", title: t("roles") }]} />
          {loading && !role ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {role && (
                <Stack gap="3">
                  <Stack orientation="horizontal" gap="1">
                    <PageHeadline>{role.name}</PageHeadline>
                    {!defaultRoleIds.includes(role.roleId) && (
                      <OverflowMenu ariaLabel={t("openRoleContextMenu")}>
                        <OverflowMenuItem
                          itemText={t("editRole")}
                          onClick={() => editRole(role)}
                        />
                        <OverflowMenuItem
                          itemText={t("delete")}
                          isDelete
                          onClick={() => {
                            deleteRole(role);
                          }}
                        />
                      </OverflowMenu>
                    )}
                  </Stack>
                  <p>
                    {t("roleId")}: {role.roleId}
                  </p>
                  {role?.description && (
                    <Description>
                      {t("description")}: {role.description}
                    </Description>
                  )}
                </Stack>
              )}
            </Flex>
          )}
        </Stack>
        {role && (
          <Section>
            <Tabs
              tabs={[
                {
                  key: "users",
                  label: t("users"),
                  content: <Members roleId={role.roleId} isOIDC={isOIDC} />,
                },
                {
                  key: "groups",
                  label: t("groups"),
                  content: (
                    <Groups
                      roleId={role.roleId}
                      isCamundaGroupsEnabled={isCamundaGroupsEnabled}
                    />
                  ),
                },
                ...(isOIDC
                  ? [
                      {
                        key: "mapping-rules",
                        label: t("mappingRules"),
                        content: <MappingRules roleId={role.roleId} />,
                      },
                      {
                        key: "clients",
                        label: t("clients"),
                        content: <Clients roleId={role.roleId} />,
                      },
                    ]
                  : []),
              ]}
              selectedTabKey={tab}
              path={`../${id}`}
            >
              <TabsHeaderList />
              <TabsContent />
            </Tabs>
          </Section>
        )}
      </>
      {editModal}
      {deleteModal}
    </StackPage>
  );
};

export default Details;
