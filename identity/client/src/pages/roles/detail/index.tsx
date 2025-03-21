/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate, useParams } from "react-router";
import { OverflowMenu, OverflowMenuItem, Section, Stack } from "@carbon/react";
import { spacing01, spacing02, spacing03 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
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
import {
  IS_ROLES_USERS_SUPPORTED,
  IS_ROLES_MAPPINGS_SUPPORTED,
} from "src/feature-flags";

const Details: FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslate("roles");
  const { id = "", tab = "details" } = useParams<{
    id: string;
    tab: string;
  }>();

  const { data: role, loading } = useApi(getRoleDetails, {
    roleKey: id,
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
                    {t("roleId")}: {role.roleKey}
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
        {(IS_ROLES_USERS_SUPPORTED || IS_ROLES_MAPPINGS_SUPPORTED) && role && (
          <Section>
            <Tabs
              tabs={[
                ...(IS_ROLES_USERS_SUPPORTED
                  ? [
                      {
                        key: "users",
                        label: t("Users"),
                        content: <div>Users</div>, //<Members groupId={group?.groupKey} />,
                      },
                    ]
                  : []),
                ...(IS_ROLES_MAPPINGS_SUPPORTED
                  ? [
                      {
                        key: "mappings",
                        label: t("Mappings"),
                        content: <div>Mappings</div>, //<Mappings groupId={group?.groupKey} />,
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
