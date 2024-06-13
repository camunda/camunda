import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateUser, User } from "src/utility/api/users";

const AddModal: FC<UseEntityModalProps<User>> = ({
  open,
  onClose,
  onSuccess,
  entity: { id, email: currentEmail, username: currentUsername },
}) => {
  const { t } = useTranslate();
  const [apiCall, { loading, namedErrors }] = useApiCall(updateUser);
  const [email, setEmail] = useState(currentEmail);
  const [username, setUsername] = useState(currentUsername);
  const [password, setPassword] = useState("");

  const handleSubmit = async () => {
    const { success } = await apiCall({
      id,
      email,
      username,
      password,
    });

    if (success) {
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("Edit user")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Updating user")}
      confirmLabel={t("Update user")}
    >
      <TextField
        label={t("Username")}
        value={username}
        placeholder={t("Username")}
        onChange={setUsername}
        errors={namedErrors?.username}
        autoFocus
      />
      <TextField
        label={t("Email")}
        value={email}
        placeholder={t("Email")}
        onChange={setEmail}
        errors={namedErrors?.email}
      />
      <TextField
        label={t("Password")}
        value={password}
        placeholder={t("Password")}
        onChange={setPassword}
        errors={namedErrors?.password}
        type="password"
        helperText={t("Leave empty to keep current password")}
      />
    </FormModal>
  );
};

export default AddModal;
