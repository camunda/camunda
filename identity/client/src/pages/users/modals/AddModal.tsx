import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createUser } from "src/utility/api/users";
import { isValidEmail } from "./isValidEmail";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate();
  const [apiCall, { loading }] = useApiCall(createUser);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [emailValid, setEmailValid] = useState(true);

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

  function validateEmail() {
    setEmailValid(isValidEmail(email));
  }

  return (
    <FormModal
      open={open}
      headline={t("Create user")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      submitDisabled={!name || !email || !username || !password}
      loadingDescription={t("Creating user...")}
      confirmLabel={t("Create user")}
    >
      <TextField
        label={t("Username")}
        value={username}
        placeholder={t("Enter username or user ID")}
        onChange={setUsername}
        autoFocus
      />
      <TextField
        label={t("Name")}
        value={name}
        placeholder={t("Enter name")}
        onChange={setName}
      />
      <TextField
        label={t("Email")}
        value={email}
        placeholder={t("Enter email address")}
        onChange={setEmail}
        type="email"
        onBlur={validateEmail}
        errors={!emailValid ? [t("Please enter a valid email")] : []}
      />
      <TextField
        label={t("Password")}
        value={password}
        placeholder={t("Password")}
        onChange={setPassword}
        type="password"
      />
    </FormModal>
  );
};

export default AddModal;
