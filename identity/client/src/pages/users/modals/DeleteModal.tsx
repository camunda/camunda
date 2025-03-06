import { FC } from "react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { deleteUser, User } from "src/utility/api/users";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<User>> = ({
  open,
  onClose,
  onSuccess,
  entity: { username },
}) => {
  const { t, Translate } = useTranslate();
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteUser);

  const handleSubmit = async () => {
    const { success } = await apiCall({
      username: username!,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("User has been deleted."),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("Delete user")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Deleting user...")}
      onClose={onClose}
      confirmLabel={t("Delete user")}
    >
      <p>
        <Translate>Are you sure you want to delete the user</Translate>{" "}
        <strong>{username}</strong>?{" "}
        <Translate>This action cannot be undone.</Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
