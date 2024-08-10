import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createUser } from "src/utility/api/users";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate();
  const [apiCall, { loading, namedErrors }] = useApiCall(createUser);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = async () => {
    const { success } = await apiCall({
      name,
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
      headline={t("Create user")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Adding user")}
      confirmLabel={t("Create user")}
    >
      <TextField
        label={t("Name")}
        value={name}
        placeholder={t("Name")}
        onChange={setName}
        errors={namedErrors?.name}
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
        label={t("Username")}
        value={username}
        placeholder={t("Username")}
        onChange={setUsername}
        errors={namedErrors?.username}
      />
      <TextField
        label={t("Password")}
        value={password}
        placeholder={t("Password")}
        onChange={setPassword}
        errors={namedErrors?.password}
        type="password"
      />
    </FormModal>
  );
};

export default AddModal;
