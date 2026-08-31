/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { Button, PageHeader } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { groupQueries } from "src/utility/api/groups/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
import { Tabs, TabsContent, TabsHeaderList } from "src/components/tabsV2";
import { useEntityModal } from "src/components/modalV2";
import EditModal from "src/pages/groups/modalsV2/EditModal";
import DeleteModal from "src/pages/groups/modalsV2/DeleteModal";
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
        {loading && !group ? (
          <DetailPageHeaderFallback hasOverflowMenu={false} />
        ) : (
          group && (
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
            >
              <PageHeader
                title={group.name}
                description={group.description}
                breadcrumb={
                  <Breadcrumbs
                    items={[
                      { href: "..", title: t("groups") },
                      { title: group.groupId },
                    ]}
                  />
                }
                actions={
                  <>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => editGroup(group)}
                    >
                      <Pencil aria-hidden="true" />
                      {t("editGroup")}
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={() => deleteGroup(group)}
                    >
                      <Trash2 aria-hidden="true" />
                      {t("delete")}
                    </Button>
                  </>
                }
                tabs={<TabsHeaderList />}
              />
              <TabsContent />
            </Tabs>
          )
        )}
      </>
      {editModal}
      {deleteModal}
    </StackPage>
  );
};

export default Details;
