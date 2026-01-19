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
import { spacing01, spacing02, spacing03 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api";
import NotFound from "src/pages/not-found";
import { Breadcrumbs, StackPage } from "src/components/layout/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";
import PageHeadline from "src/components/layout/PageHeadline";
import Tabs from "src/components/tabs";
import { getRoleDetails } from "src/utility/api/roles";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/roles/modals/DeleteModal";
import { Description } from "src/components/layout/DetailsPageDescription";
import Members from "src/pages/roles/detail/members";
import Groups from "src/pages/roles/detail/groups";
import MappingRules from "src/pages/roles/detail/mapping-rules";
import Clients from "src/pages/roles/detail/clients";
import { isOIDC } from "src/configuration";

const Details: FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslate("roles");
  const { id = "", tab = "details" } = useParams<{
    id: string;
    tab: string;
  }>();

  const { data: role, loading } = useApi(getRoleDetails, {
    roleId: id,
  });

  const [deleteRole, deleteModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  if (!loading && !role) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap={spacing02}>
          <Breadcrumbs items={[{ href: "/roles", title: t("roles") }]} />
          {loading && !role ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {role && (
                <Stack gap={spacing03}>
                  <Stack orientation="horizontal" gap={spacing01}>
                    <PageHeadline>{role.name}</PageHeadline>
                    <OverflowMenu ariaLabel={t("openRoleContextMenu")}>
                      <OverflowMenuItem
                        itemText={t("delete")}
                        isDelete
                        onClick={() => {
                          deleteRole(role);
                        }}
                      />
                    </OverflowMenu>
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
                  content: <Members roleId={role.roleId} />,
                },
                {
                  key: "groups",
                  label: t("groups"),
                  content: <Groups roleId={role.roleId} />,
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
            />
          </Section>
        )}
      </>
      {deleteModal}
    </StackPage>
  );
};

export default Details;
