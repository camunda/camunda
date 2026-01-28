/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PasswordInput, TextInput } from "@carbon/react";
import { UserAdmin } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import CamundaLogo from "src/assets/images/camunda.svg";
import {
  SetupFormContainer,
  SetupPageContainer,
  Content,
  Header,
  PageTitle,
  InlineNotification,
  Button,
} from "src/pages/setup/styled.ts";
import Divider from "src/components/form/Divider";
import { useApiCall } from "src/utility/api";
import { createAdminUser } from "src/utility/api/setup";
import { ErrorResponse } from "src/utility/api/request";
import { isValidEmail } from "src/utility/validate";

interface SetupFormProps {
  onSuccess: () => void;
}

const SetupForm: React.FC<SetupFormProps> = ({ onSuccess }) => {
  const { t } = useTranslate();
  const [apiCall] = useApiCall(createAdminUser, {
    suppressErrorNotification: true,
  });

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
      const { success, error } = await apiCall({
        name,
        email,
        username,
        password,
      });

      if (success) {
        onSuccess();
      } else {
        const detail = (error as ErrorResponse<"detailed">)?.detail;
        setSubmitError(detail || t("setupCreateAdminUserGenericError"));
      }
    }
  };

  return (
    <SetupFormContainer $hasError={!!submitError}>
      {submitError && (
        <InlineNotification
          title={submitError}
          hideCloseButton
          kind="error"
          role="alert"
          lowContrast
        />
      )}
      <TextInput
        id="username"
        name="username"
        value={username}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setUsername(e.target.value.trim())
        }
        labelText={t("setupUsernameLabel")}
        invalid={usernameError.hasError}
        invalidText={usernameError.errorText}
        placeholder={t("setupUsernamePlaceholder")}
        onBlur={({ target }) => {
          if (target.value.trim().length < 1) {
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
      <PasswordInput
        id="password"
        name="password"
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setPassword(e.target.value.trim())
        }
        value={password}
        type="password"
        hidePasswordLabel={t("hidePassword")}
        showPasswordLabel={t("showPassword")}
        labelText={t("setupPasswordLabel")}
        invalid={passwordError.hasError}
        invalidText={passwordError.errorText}
        placeholder={t("setupPasswordPlaceholder")}
        helperText={t("setupPasswordHelperText")}
        onBlur={({ target }) => {
          if (target.value.trim().length < 1) {
            setPasswordError({
              hasError: true,
              errorText: t("setupPasswordRequired"),
            });
          } else if (
            target.value.trim().length < 6 ||
            !/\d/.test(target.value)
          ) {
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
      <PasswordInput
        id="confirmPassword"
        name="confirmPassword"
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setConfirmPassword(e.target.value.trim())
        }
        value={confirmPassword}
        type="password"
        hidePasswordLabel={t("hidePassword")}
        showPasswordLabel={t("showPassword")}
        labelText={t("setupConfirmPasswordLabel")}
        invalid={confirmPasswordError.hasError}
        invalidText={confirmPasswordError.errorText}
        placeholder={t("setupConfirmPasswordPlaceholder")}
        onBlur={({ target }) => {
          if (target.value.trim().length < 1) {
            setConfirmPasswordError({
              hasError: true,
              errorText: t("setupConfirmPasswordRequired"),
            });
          } else if (target.value.trim() !== password.trim()) {
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
      <Divider $highContrast $noMargin />
      <TextInput
        id="name"
        name="name"
        value={name}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setName(e.target.value.trim())
        }
        labelText={t("setupNameLabel")}
        placeholder={t("setupNamePlaceholder")}
      />
      <TextInput
        id="email"
        name="email"
        value={email}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setEmail(e.target.value.trim())
        }
        labelText={t("setupEmailLabel")}
        invalid={emailError.hasError}
        invalidText={emailError.errorText}
        placeholder={t("setupEmailPlaceholder")}
        onBlur={({ target }) => {
          if (
            target.value.trim().length > 1 &&
            !isValidEmail(target.value.trim())
          ) {
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
        renderIcon={UserAdmin}
      >
        {t("setupCreateUser")}
      </Button>
    </SetupFormContainer>
  );
};

export const SetupPage: React.FC = () => {
  const { t } = useTranslate();
  const navigate = useNavigate();

  const onSuccess = () => {
    void navigate(`/login`);
  };
  return (
    <SetupPageContainer>
      <Content>
        <Header>
          <CamundaLogo />
        </Header>
        <PageTitle>{t("setupCreateAdminUser")}</PageTitle>
        <SetupForm onSuccess={onSuccess} />
      </Content>
    </SetupPageContainer>
  );
};
