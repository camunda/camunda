/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import Divider from "src/components/form/Divider";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateUser, User } from "src/utility/api/users";
import { isValidEmail } from "src/utility/isValidEmail";

type FormData = Pick<User, "name" | "email"> & {
  password: string;
  repeatedPassword: string;
};

const EditModal: FC<UseEntityModalProps<User>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("users");
  const [callUpdateUser, { loading }] = useApiCall(updateUser);

  const {
    control,
    handleSubmit,
    formState: { touchedFields },
    watch,
    getValues,
    trigger,
  } = useForm<FormData>({
    defaultValues: {
      name: entity.name,
      email: entity.email,
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
    const { success } = await callUpdateUser({
      username: entity.username,
      name: data.name,
      email: data.email,
      password: data.password,
    });
    if (success) {
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("editUserWithName", { username: entity.username })}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      loadingDescription={t("updatingUser")}
      confirmLabel={t("updateUser")}
    >
      <Controller
        name="name"
        control={control}
        rules={{ required: t("nameRequired") }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("name")}
            placeholder={t("enterName")}
            errors={fieldState.error?.message}
            autoFocus
          />
        )}
      />
      <Controller
        name="email"
        control={control}
        rules={{
          required: t("emailRequired"),
          validate: (value) =>
            isValidEmail(value) || t("pleaseEnterValidEmail"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("email")}
            placeholder={t("enterEmailAddress")}
            errors={fieldState.error?.message}
          />
        )}
      />
      <Divider />
      <h3>{t("resetPassword")}</h3>
      <div>{t("resetPasswordInfo")}</div>
      <Controller
        name="password"
        control={control}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("newPassword")}
            placeholder={t("enterNewPassword")}
            type="password"
            helperText={t("keepCurrentPassword")}
          />
        )}
      />
      <Controller
        name="repeatedPassword"
        control={control}
        rules={{
          validate: (value) => {
            const pwd = getValues("password");
            if (pwd && value.trim().length === 0)
              return t("repeatPasswordRequired");
            if (!pwd) return true;
            return value === pwd || t("pleaseEnterValidPassword");
          },
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

export default EditModal;
