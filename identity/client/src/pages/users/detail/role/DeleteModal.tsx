import { FC } from "react";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { useParams } from "react-router";
import { removeUserRole } from "src/utility/api/users/roles";
import { Role } from "src/utility/api/roles";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<Role>> = ({
  entity: role,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const { id = "" } = useParams<{ id: string }>();
  const [callRemoveRole, { loading }] = useApiCall(removeUserRole);

  const handleSubmit = async () => {
    if (role) {
      const { success } = await callRemoveRole({
        id,
        roleId: role.id,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("Role has been removed."),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t(
        'Are you sure you want to remove the role "{{ name }}" from the user?',
        {
          name: role?.name,
        },
      )}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Removing role")}
      onClose={onClose}
    />
  );
};

export default DeleteModal;
