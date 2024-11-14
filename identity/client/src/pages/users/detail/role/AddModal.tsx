import { ChangeEvent, FC, useEffect, useState } from "react";
import { Checkbox, CheckboxSkeleton } from "@carbon/react";
import useTranslate from "src/utility/localization";
import { AddFormModal, UseEntityModalCustomProps } from "src/components/modal";
import { useApi, useApiCall } from "src/utility/api/hooks";
import { User } from "src/utility/api/users";
import { assignUserRole } from "src/utility/api/users/roles";
import { searchRoles, Role } from "src/utility/api/roles";
import { useNavigate } from "react-router";
import {
  TranslatedErrorInlineNotification,
  TranslatedInlineNotification,
} from "src/components/notifications/InlineNotification";
import ascendingSort from "src/utility/ascendingSort";

const AddModal: FC<UseEntityModalCustomProps<User, { userRoles: Role[] }>> = ({
  open,
  onClose,
  onSuccess,
  entity: user,
  userRoles,
}) => {
  const { t } = useTranslate();
  const navigate = useNavigate();

  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  const [showSelectRoleError, setShowSelectRoleError] = useState(false);

  const {
    data: roles,
    loading: loadingRoles,
    reload: reloadRoles,
    success: getRolesSuccess,
  } = useApi(searchRoles);

  const [callAssignRole, { loading: loadingAssignRole }] =
    useApiCall(assignUserRole);

  const loading = loadingRoles || loadingAssignRole;

  const unassignedRoles = (
    userRoles
      ? roles?.filter(({ id }) => !userRoles.some((role) => role.id === id))
      : roles
  )?.sort((a, b) => ascendingSort(a.name, b.name));

  const handleSubmit = async () => {
    if (!user) return;

    if (selectedRoles.length === 0) {
      setShowSelectRoleError(true);
      return;
    }

    setShowSelectRoleError(false);

    const results = await Promise.all(
      selectedRoles.map((roleId) => callAssignRole({ id: user.id!, roleId })),
    );

    if (results.every(({ success }) => success)) {
      onSuccess();
    }
  };

  const goToRolesPage = () => navigate("/roles");

  useEffect(() => {
    if (open) {
      setSelectedRoles([]);
      setShowSelectRoleError(false);
    }
  }, [open]);

  const onRoleChange =
    (roleId: string) =>
    (_: ChangeEvent, { checked }: { checked: boolean }) =>
      setSelectedRoles(
        checked
          ? [...selectedRoles, roleId]
          : selectedRoles.filter((id) => id !== roleId),
      );

  return (
    <AddFormModal
      open={open}
      loading={loading}
      loadingDescription={t("Assigning role")}
      headline={t("Assign roles to user")}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      {showSelectRoleError && (
        <TranslatedErrorInlineNotification title="Please select at least one role." />
      )}
      {loadingRoles && (!roles || roles.length === 0) && <CheckboxSkeleton />}
      {unassignedRoles && (
        <fieldset>
          <legend>{t("Select one or multiple roles")}</legend>
          {unassignedRoles.map(({ id, name, description }) => (
            <Checkbox
              key={name}
              id={id}
              labelText={`${name} ${description ? `(${description})` : ""}`}
              checked={selectedRoles.some((roleId) => roleId === id)}
              onChange={onRoleChange(id)}
            />
          ))}
        </fieldset>
      )}
      {!loading && !getRolesSuccess && (
        <TranslatedErrorInlineNotification
          title="The list of roles could not be loaded."
          actionButton={{ label: "Retry", onClick: reloadRoles }}
        />
      )}
      {!loading && getRolesSuccess && roles?.length === 0 && (
        <TranslatedInlineNotification
          title="Please configure a role first, then come back to assign it."
          actionButton={{ label: "Go to roles", onClick: goToRolesPage }}
        />
      )}
      {!loading &&
        getRolesSuccess &&
        roles &&
        roles.length > 0 &&
        roles.length === userRoles?.length && (
          <TranslatedInlineNotification
            title="All configured roles are already assigned to the user. You can configure a new role and then assign it to the user."
            actionButton={{ label: "Go to roles", onClick: goToRolesPage }}
          />
        )}
    </AddFormModal>
  );
};

export default AddModal;
