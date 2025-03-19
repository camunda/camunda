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
import { getGroupDetails } from "src/utility/api/groups";
import { useEntityModal } from "src/components/modal";
import EditModal from "src/pages/groups/modals/EditModal";
import DeleteModal from "src/pages/groups/modals/DeleteModal";
import { Description } from "src/pages/groups/detail/components";
import {
  IS_GROUP_USERS_SUPPORTED,
  IS_GROUP_ROLES_SUPPORTED,
  IS_GROUP_MAPPINGS_SUPPORTED,
} from "src/feature-flags";
import Members from "src/pages/groups/detail/members";
import Roles from "src/pages/groups/detail/roles";
import Mappings from "src/pages/groups/detail/mappings";

const Details: FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslate("groups");
  const { id = "", tab = "details" } = useParams<{
    id: string;
    tab: string;
  }>();

  const {
    data: group,
    loading,
    reload,
  } = useApi(getGroupDetails, { groupKey: id });
  const [editGroup, editModal] = useEntityModal(EditModal, reload);
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  if (!loading && !group) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap={spacing02}>
          <Breadcrumbs items={[{ href: "/groups", title: t("groups") }]} />
          {loading && !group ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {group && (
                <Stack gap={spacing03}>
                  <Stack orientation="horizontal" gap={spacing01}>
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
                    {t("groupId")}: {group.groupKey}
                  </p>
                  {group?.description && (
                    <Description>
                      {t("description")}: {group.description}
                    </Description>
                  )}
                </Stack>
              )}
            </Flex>
          )}
        </Stack>
        {(IS_GROUP_USERS_SUPPORTED ||
          IS_GROUP_ROLES_SUPPORTED ||
          IS_GROUP_MAPPINGS_SUPPORTED) &&
          group && (
            <Section>
              <Tabs
                tabs={[
                  ...(IS_GROUP_USERS_SUPPORTED
                    ? [
                        {
                          key: "users",
                          label: t("Users"),
                          content: <Members groupId={group?.groupKey} />,
                        },
                      ]
                    : []),
                  ...(IS_GROUP_ROLES_SUPPORTED
                    ? [
                        {
                          key: "roles",
                          label: t("Roles"),
                          content: <Roles groupId={group?.groupKey} />,
                        },
                      ]
                    : []),
                  ...(IS_GROUP_MAPPINGS_SUPPORTED
                    ? [
                        {
                          key: "mappings",
                          label: t("Mappings"),
                          content: <Mappings groupId={group?.groupKey} />,
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
