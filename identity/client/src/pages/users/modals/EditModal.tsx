/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateUser, User } from "src/utility/api/users";
import { isValidEmail } from "./isValidEmail";

const EditModal: FC<UseEntityModalProps<User>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate();
  const [callUpdateUser, { loading }] = useApiCall(updateUser);
  const [user, setUser] = useState<User>(entity);
  const [emailValid, setEmailValid] = useState(true);

  const handleSubmit = async () => {
    const { success } = await callUpdateUser(user);
    if (success) {
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("editUser")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("updatingUser")}
      confirmLabel={t("updateUser")}
    >
      <TextField
        label={t("username")}
        value={user.username}
        placeholder={t("enterUsername")}
        readOnly
      />
      <TextField
        label={t("name")}
        value={user.name}
        placeholder={t("enterName")}
        onChange={(name) => setUser({ ...user, name })}
        autoFocus
      />
      <TextField
        label={t("email")}
        value={user.email}
        placeholder={t("enterEmailAddress")}
        onChange={(email) => setUser({ ...user, email })}
        onBlur={() => setEmailValid(isValidEmail(user.email))}
        errors={!emailValid ? [t("validEmail")] : []}
      />
      <TextField
        label={t("newPassword")}
        value={user.password}
        placeholder={t("enterNewPassword")}
        onChange={(password) => setUser({ ...user, password })}
        type="password"
        helperText={t("keepCurrentPassword")}
      />
    </FormModal>
  );
};

export default EditModal;
