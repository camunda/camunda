/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { useForm, Controller } from "react-hook-form";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createUser } from "src/utility/api/users";
import { isValidEmail, isValidId, getIdPattern } from "src/utility/validate";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("users");
  const [apiCall, { loading, error }] = useApiCall(createUser, {
    suppressErrorNotification: true,
  });

  type FormData = {
    username: string;
    name: string;
    email: string;
    password: string;
    repeatedPassword: string;
  };

  const {
    control,
    handleSubmit,
    formState: { touchedFields },
    getValues,
    watch,
    trigger,
  } = useForm<FormData>({
    defaultValues: {
      username: "",
      name: "",
      email: "",
      password: "",
      repeatedPassword: "",
    },
    mode: "all",
  });

  const password = watch("password");
  useEffect(() => {
    if (touchedFields.repeatedPassword) {
      void trigger("repeatedPassword");
    }
  }, [password, touchedFields.repeatedPassword, trigger]);

  const onSubmit = async (data: FormData) => {
    const { success } = await apiCall({
      name: data.name,
      email: data.email,
      username: data.username,
      password: data.password,
    });

    if (success) {
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("createUser")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      error={error}
      loadingDescription={t("creatingUser")}
      confirmLabel={t("createUser")}
    >
      <Controller
        name="username"
        control={control}
        rules={{
          required: t("usernameRequired"),
          validate: (value) =>
            isValidId(value) ||
            t("pleaseEnterValidUsername", {
              pattern: getIdPattern(),
            }),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("username")}
            placeholder={t("enterUsernameOrUserId")}
            errors={fieldState.error?.message}
            autoFocus
          />
        )}
      />
      <Controller
        name="name"
        control={control}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("name")}
            placeholder={t("enterName")}
            errors={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="email"
        control={control}
        rules={{
          validate: (value) =>
            !value || isValidEmail(value) || t("pleaseEnterValidEmail"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("email")}
            placeholder={t("enterEmailAddress")}
            type="email"
            errors={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="password"
        control={control}
        rules={{ required: t("passwordRequired") }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("password")}
            placeholder={t("password")}
            type="password"
            errors={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="repeatedPassword"
        control={control}
        rules={{
          required: t("repeatPasswordRequired"),
          validate: (value) =>
            value === getValues("password") || t("pleaseEnterValidPassword"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("repeatPassword")}
            placeholder={t("repeatPassword")}
            type="password"
            errors={fieldState.error?.message}
          />
        )}
      />
    </FormModal>
  );
};

export default AddModal;
