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
import Divider from "src/components/form/Divider";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateUser, User } from "src/utility/api/users";
import { isValidEmail } from "./isValidEmail";

const EditModal: FC<UseEntityModalProps<User>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("users");
  const [callUpdateUser, { loading }] = useApiCall(updateUser);
  const [user, setUser] = useState<User>(entity);
  const [emailValid, setEmailValid] = useState(true);
  const [repeatedPassword, setRepeatedPassword] = useState("");
  const [passwordValid, setPasswordValid] = useState(true);

  const handleSubmit = async () => {
    const { success } = await callUpdateUser(user);
    if (success) {
      onSuccess();
    }
  };

  function validatePassword() {
    if (user.password && repeatedPassword) {
      setPasswordValid(user.password === repeatedPassword);
    }
  }

  return (
    <FormModal
      open={open}
      headline={t("editUserWithName", { username: user.username })}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("updatingUser")}
      confirmLabel={t("updateUser")}
      submitDisabled={
        (user.password && repeatedPassword === "") || // repeat password field is empty
        (repeatedPassword !== "" && user.password !== repeatedPassword) // passwords do not match
      }
    >
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
      <Divider />
      <h3>{t("resetPassword")}</h3>
      <div>{t("resetPasswordInfo")}</div>
      <TextField
        label={t("newPassword")}
        value={user.password}
        placeholder={t("enterNewPassword")}
        onChange={(password) => setUser({ ...user, password })}
        onBlur={validatePassword}
        type="password"
        helperText={t("keepCurrentPassword")}
      />
      <TextField
        label={t("repeatPassword")}
        value={repeatedPassword}
        placeholder={t("repeatPassword")}
        onChange={setRepeatedPassword}
        onBlur={validatePassword}
        type="password"
        errors={!passwordValid ? [t("pleaseEnterValidPassword")] : []}
      />
    </FormModal>
  );
};

export default EditModal;
