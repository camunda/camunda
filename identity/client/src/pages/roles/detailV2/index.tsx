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
import { roleQueries } from "src/utility/api/roles/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
import { Tabs, TabsContent, TabsHeaderList } from "src/components/tabsV2";
import { useEntityModal } from "src/components/modalV2";
import DeleteModal from "src/pages/roles/modalsV2/DeleteModal";
import EditModal from "src/pages/roles/modalsV2/EditModal";
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
        {loading && !role ? (
          <DetailPageHeaderFallback hasOverflowMenu={false} />
        ) : (
          role && (
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
              <PageHeader
                title={role.name}
                description={role.description ?? undefined}
                breadcrumb={
                  <Breadcrumbs
                    items={[
                      { href: "..", title: t("roles") },
                      { title: role.roleId },
                    ]}
                  />
                }
                actions={
                  !defaultRoleIds.includes(role.roleId) && (
                    <>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => editRole(role)}
                      >
                        <Pencil aria-hidden="true" />
                        {t("editRole")}
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        onClick={() => deleteRole(role)}
                      >
                        <Trash2 aria-hidden="true" />
                        {t("delete")}
                      </Button>
                    </>
                  )
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
