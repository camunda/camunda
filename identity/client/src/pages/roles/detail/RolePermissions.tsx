import { FC } from "react";
import useTranslate from "src/utility/localization";
import { Role } from "src/utility/api/roles";
import EntityList from "src/components/entityList";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import usePermissionsTranslated from "src/pages/roles/modals/usePermissionsTranslated";

type RoleDetailsProps = {
  role: Role | null;
  loading: boolean;
};

const RolePermissions: FC<RoleDetailsProps> = ({ role, loading }) => {
  const { t } = useTranslate();

  const availableItems = usePermissionsTranslated(role?.permissions);

  if (availableItems.length == 0) {
    return (
      <>
        <C3EmptyState
          heading={t("Assign permissions")}
          description={t(
            "No permission is assigned to this Role, use Edit to add some",
          )}
          button={{
            label: t("Edit Role"),
            onClick: () => {},
          }}
          link={{
            label: t("Learn more about roles"),
            href: `/identity/concepts/access-control/roles`,
          }}
        />
      </>
    );
  }

  return (
    <EntityList
      isInsideModal={false}
      title={t("Permissions")}
      data={availableItems}
      headers={[
        { header: t("Permission"), key: "permission" },
        { header: t("Description"), key: "description" },
      ]}
      loading={loading}
    />
  );
};

export default RolePermissions;
