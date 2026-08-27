/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Heading, Separator } from "@camunda/design-system";
import { UserCog } from "lucide-react";
import useTranslate from "src/utility/localization";
import CamundaLogo from "src/assets/images/camunda.svg";
import Page from "src/components/layoutV2/Page";
import TextField from "src/components/formV2/TextField";
import { ErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import { useMutation } from "@tanstack/react-query";
import { setupMutations } from "src/utility/api/setup/mutations";
import { ApiError, isDetailedError } from "src/utility/api/request";
import { isValidEmail } from "src/utility/validate";

interface SetupFormProps {
  onSuccess: () => void;
}

const SetupForm: React.FC<SetupFormProps> = ({ onSuccess }) => {
  const { t } = useTranslate();
  const { mutateAsync: apiCall } = useMutation(
    setupMutations.createAdminUser(),
  );

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [usernameError, setUsernameError] = useState({
    hasError: false,
    errorText: "",
  });
  const [passwordError, setPasswordError] = useState({
    hasError: false,
    errorText: "",
  });
  const [confirmPasswordError, setConfirmPasswordError] = useState({
    hasError: false,
    errorText: "",
  });
  const [emailError, setEmailError] = useState({
    hasError: false,
    errorText: "",
  });
  const [submitError, setSubmitError] = useState("");

  const handleSubmit = async () => {
    if (username && password && confirmPassword) {
      try {
        await apiCall({ name, email, username, password });
        onSuccess();
      } catch (error) {
        const detail =
          error instanceof ApiError && error.body && isDetailedError(error.body)
            ? error.body.detail
            : undefined;
        setSubmitError(detail || t("setupCreateAdminUserGenericError"));
      }
    }
  };

  return (
    <div className="flex w-full flex-col gap-6">
      {submitError && <ErrorInlineNotification title={submitError} />}
      <TextField
        label={t("setupUsernameLabel")}
        value={username}
        onChange={(value) => setUsername(value.trim())}
        errors={usernameError.hasError ? usernameError.errorText : undefined}
        placeholder={t("setupUsernamePlaceholder")}
        onBlur={(value) => {
          if (value.trim().length < 1) {
            setUsernameError({
              hasError: true,
              errorText: t("setupUsernameRequired"),
            });
          } else {
            setUsernameError({
              hasError: false,
              errorText: "",
            });
          }
        }}
      />
      <TextField
        type="password"
        label={t("setupPasswordLabel")}
        value={password}
        onChange={(value) => setPassword(value.trim())}
        errors={passwordError.hasError ? passwordError.errorText : undefined}
        placeholder={t("setupPasswordPlaceholder")}
        helperText={t("setupPasswordHelperText")}
        onBlur={(value) => {
          if (value.trim().length < 1) {
            setPasswordError({
              hasError: true,
              errorText: t("setupPasswordRequired"),
            });
          } else if (value.trim().length < 6 || !/\d/.test(value)) {
            setPasswordError({
              hasError: true,
              errorText: t("setupPasswordHelperText"),
            });
          } else {
            setPasswordError({
              hasError: false,
              errorText: "",
            });
          }
        }}
      />
      <TextField
        type="password"
        label={t("setupConfirmPasswordLabel")}
        value={confirmPassword}
        onChange={(value) => setConfirmPassword(value.trim())}
        errors={
          confirmPasswordError.hasError
            ? confirmPasswordError.errorText
            : undefined
        }
        placeholder={t("setupConfirmPasswordPlaceholder")}
        onBlur={(value) => {
          if (value.trim().length < 1) {
            setConfirmPasswordError({
              hasError: true,
              errorText: t("setupConfirmPasswordRequired"),
            });
          } else if (value.trim() !== password.trim()) {
            setConfirmPasswordError({
              hasError: true,
              errorText: t("setupConfirmPasswordMismatch"),
            });
          } else {
            setConfirmPasswordError({
              hasError: false,
              errorText: "",
            });
          }
        }}
      />
      <Separator />
      <TextField
        label={t("setupNameLabel")}
        value={name}
        onChange={(value) => setName(value.trim())}
        placeholder={t("setupNamePlaceholder")}
      />
      <TextField
        label={t("setupEmailLabel")}
        value={email}
        onChange={(value) => setEmail(value.trim())}
        errors={emailError.hasError ? emailError.errorText : undefined}
        placeholder={t("setupEmailPlaceholder")}
        onBlur={(value) => {
          if (value.trim().length > 1 && !isValidEmail(value.trim())) {
            setEmailError({
              hasError: true,
              errorText: t("setupEmailInvalid"),
            });
          } else {
            setEmailError({
              hasError: false,
              errorText: "",
            });
          }
        }}
      />
      <Button
        type="button"
        onClick={handleSubmit}
        disabled={
          !username ||
          !password ||
          !confirmPassword ||
          usernameError.hasError ||
          passwordError.hasError ||
          confirmPasswordError.hasError ||
          emailError.hasError
        }
      >
        <UserCog aria-hidden="true" />
        {t("setupCreateUser")}
      </Button>
    </div>
  );
};

export const SetupPage: React.FC = () => {
  const { t } = useTranslate();
  const navigate = useNavigate();

  const onSuccess = () => {
    void navigate(`/login`);
  };
  return (
    <Page className="min-h-screen">
      <div className="m-auto flex w-100 flex-col gap-6">
        <div className="flex justify-center">
          <CamundaLogo />
        </div>
        <Heading as="h1" variant="heading-lg" className="text-center">
          {t("setupCreateAdminUser")}
        </Heading>
        <SetupForm onSuccess={onSuccess} />
      </div>
    </Page>
  );
};
