import { FC } from "react";
import useTranslate from "src/utility/localization";
import { Role } from "src/utility/api/roles";
import EntityList from "src/components/entityList";
import usePermissions from "src/pages/roles/modals/usePermissions";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { useEntityModal } from "src/components/modal";
import EditModal from "src/pages/roles/modals/EditModal";
import { useNavigate } from "react-router";

type RoleDetailsProps = {
  role: Role | null;
  loading: boolean;
};

const RolePermissions: FC<RoleDetailsProps> = ({ role, loading }) => {
  const navigate = useNavigate();
  const { t } = useTranslate();

  const { availableItems } = usePermissions();

  const rolePermissions = availableItems.filter((itemX) =>
    role?.permissions?.includes(itemX.permission),
  );
  const [editRole, editRoleModal] = useEntityModal(EditModal, () =>
    navigate("..", { replace: true }),
  );
  const openEditRole = () => editRole(role!);

  if (rolePermissions.length == 0) {
    return (
      <>
        <C3EmptyState
          heading={t("Assign permissions")}
          description={t(
            "No permission is assigned to this Role, use Edit to add some",
          )}
          button={{
            label: t("Edit Role"),
            onClick: openEditRole,
          }}
          link={{
            label: t("Learn more about roles"),
            href: `/identity/concepts/access-control/roles`,
          }}
        />
        {editRoleModal}
      </>
    );
  }

  return (
    <EntityList
      isInsideModal={false}
      title={t("Permissions")}
      data={rolePermissions}
      headers={[
        { header: t("Permission"), key: "permission" },
        { header: t("Description"), key: "description" },
      ]}
      loading={loading}
    />
  );
};

export default RolePermissions;
