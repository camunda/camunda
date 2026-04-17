import { FC } from "react";
import { useNavigate, useParams } from "react-router";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import NotFound from "src/pages/not-found";
import { OverflowMenu, OverflowMenuItem, Section } from "@carbon/react";
import { StackPage } from "src/components/layout/Page";
import PageHeadline from "src/components/layout/PageHeadline";
import { getRole } from "src/utility/api/roles";
import RoleDetails from "./RoleDetails";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/roles/modals/DeleteModal";
import Flex from "src/components/layout/Flex";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Tabs from "src/components/tabs";
import EditModal from "src/pages/roles/modals/EditModal";
<<<<<<< HEAD
import RolePermissions from "src/pages/roles/detail/RolePermissions";
=======
import { Description } from "src/components/layout/DetailsPageDescription";
import Members from "src/pages/roles/detail/members";
import Groups from "src/pages/roles/detail/groups";
import MappingRules from "src/pages/roles/detail/mapping-rules";
import Clients from "src/pages/roles/detail/clients";
import { isProtectedRole } from "src/pages/roles/protected-roles";
>>>>>>> 7f3381119 (feat: Read-only roles)

const Details: FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslate();
  const { id = "", tab = "details" } = useParams<{
    id: string;
    tab: string;
  }>();

  const { data: role, loading } = useApi(getRole, {
    id,
  });

  const [deleteRole, deleteModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  const [editRole, editRoleModal] = useEntityModal(EditModal, () =>
    navigate("..", { replace: true }),
  );

  if (!loading && !role) return <NotFound />;

  return (
    <StackPage>
      <>
<<<<<<< HEAD
        {loading && !role ? (
          <DetailPageHeaderFallback />
        ) : (
          <Flex>
            {role && (
              <>
                <PageHeadline>{role.name}</PageHeadline>
                <OverflowMenu ariaLabel={t("Open role context menu")}>
                  <OverflowMenuItem
                    itemText={t("Delete")}
                    isDelete
                    onClick={() => {
                      deleteRole(role);
                    }}
                  />
                  <OverflowMenuItem
                    itemText={t("Edit")}
                    onClick={() => {
                      editRole(role);
                    }}
                  />
                </OverflowMenu>
              </>
            )}
          </Flex>
=======
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
                    {!isProtectedRole(role.roleId) && (
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
            />
          </Section>
>>>>>>> 7f3381119 (feat: Read-only roles)
        )}
        <Section>
          <Tabs
            tabs={[
              {
                key: "details",
                label: t("Role details"),
                content: <RoleDetails role={role} loading={loading} />,
              },
              {
                key: "permissions",
                label: t("Permissions"),
                content: <RolePermissions role={role} loading={loading} />,
              },
            ]}
            selectedTabKey={tab}
            path={`../${id}`}
          />
        </Section>
      </>
      {deleteModal}
      {editRoleModal}
    </StackPage>
  );
};

export default Details;
