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
import { FormModal, UseModalProps } from "src/components/modal";
import { createUser } from "src/utility/api/users";
import { isValidEmail } from "./isValidEmail";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("users");
  const [apiCall, { loading, error }] = useApiCall(createUser, {
    suppressErrorNotification: true,
  });
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [repeatedPassword, setRepeatedPassword] = useState("");
  const [emailValid, setEmailValid] = useState(true);
  const [passwordValid, setPasswordValid] = useState(true);

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

  function validatePassword() {
    if (password && repeatedPassword) {
      setPasswordValid(password === repeatedPassword);
    }
  }

  return (
    <FormModal
      open={open}
      headline={t("createUser")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      error={error}
      submitDisabled={
        !name ||
        !email ||
        !username ||
        !password ||
        password !== repeatedPassword
      }
      loadingDescription={t("creatingUser")}
      confirmLabel={t("createUser")}
    >
      <TextField
        label={t("username")}
        value={username}
        placeholder={t("enterUsernameOrUserId")}
        onChange={setUsername}
        autoFocus
      />
      <TextField
        label={t("name")}
        value={name}
        placeholder={t("enterName")}
        onChange={setName}
      />
      <TextField
        label={t("email")}
        value={email}
        placeholder={t("enterEmailAddress")}
        onChange={setEmail}
        type="email"
        onBlur={validateEmail}
        errors={!emailValid ? [t("pleaseEnterValidEmail")] : []}
      />
      <TextField
        label={t("password")}
        value={password}
        placeholder={t("password")}
        onChange={setPassword}
        onBlur={validatePassword}
        type="password"
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

export default AddModal;
