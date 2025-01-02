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
import { spacing02 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import NotFound from "src/pages/not-found";
import { Breadcrumbs, StackPage } from "src/components/layout/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";
import PageHeadline from "src/components/layout/PageHeadline";
import Tabs from "src/components/tabs";
import { getGroupDetails } from "src/utility/api/groups";
import Members from "src/pages/groups/detail/members";
import { useEntityModal } from "src/components/modal";
import EditModal from "src/pages/groups/modals/EditModal";
import DeleteModal from "src/pages/groups/modals/DeleteModal";

const Details: FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslate();
  const { id = "" } = useParams<{
    id: string;
  }>();

  const {
    data: group,
    loading,
    reload,
  } = useApi(getGroupDetails, { groupKey: id });
  const [renameGroup, editModal] = useEntityModal(EditModal, reload);
  const [deleteGroup, deleteModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  if (!loading && !group) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap={spacing02}>
          <Breadcrumbs items={[{ href: "/groups", title: t("Groups") }]} />
          {loading && !group ? (
            <DetailPageHeaderFallback />
          ) : (
            <Flex>
              {group && (
                <>
                  <PageHeadline>{group.name}</PageHeadline>

                  <OverflowMenu ariaLabel={t("Open group context menu")}>
                    <OverflowMenuItem
                      itemText={t("Rename")}
                      onClick={() => {
                        renameGroup(group);
                      }}
                    />
                    <OverflowMenuItem
                      itemText={t("Delete")}
                      isDelete
                      onClick={() => {
                        deleteGroup(group);
                      }}
                    />
                  </OverflowMenu>
                </>
              )}
            </Flex>
          )}
        </Stack>
        {group && (
          <Section>
            <Tabs
              tabs={[
                {
                  key: "members",
                  label: t("Members"),
                  content: <Members groupId={group?.groupKey} />,
                },
              ]}
              selectedTabKey="members"
              path={`../${id}`}
            />
          </Section>
        )}
        <>{editModal}</>
        <>{deleteModal}</>
      </>
    </StackPage>
  );
};

export default Details;
