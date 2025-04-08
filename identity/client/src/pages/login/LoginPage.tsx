/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useCallback, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  Button,
  InlineNotification,
  Link,
  PasswordInput,
  TextInput,
} from "@carbon/react";
import useTranslate from "src/utility/localization";
import { login } from "src/utility/auth";
import { getCopyrightNoticeText } from "src/utility/copyright.ts";
import CamundaLogo from "src/assets/images/camunda.svg";
import { useLicense } from "src/utility/license.ts";
import {
  LoginFormContainer,
  LoginPageContainer,
  Content,
  CopyrightNotice,
  Header,
  LicenseInfo,
} from "src/pages/login/styled.ts";

interface LoginFormProps {
  onSuccess: () => void;
}

const LoginForm: React.FC<LoginFormProps> = ({ onSuccess }) => {
  const { t } = useTranslate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [usernameError, setUsernameError] = useState({
    hasError: false,
    errorText: "",
  });
  const [passwordError, setPasswordError] = useState({
    hasError: false,
    errorText: "",
  });
  const [submitError, setSubmitError] = useState("");

  const submit = useCallback(() => {
    if (!username) {
      setUsernameError({
        hasError: true,
        errorText: "Username is required",
      });
    }

    if (!password) {
      setPasswordError({
        hasError: true,
        errorText: "Password is required",
      });
    }

    if (username && password) {
      login(username, password).then(({ success, message }) => {
        if (success) {
          onSuccess();
        } else {
          setSubmitError(message);
        }
      });
    }
  }, [onSuccess, username, password]);

  return (
    <LoginFormContainer $hasError={!!submitError}>
      {submitError && (
        <InlineNotification
          title={submitError}
          hideCloseButton
          kind="error"
          role="alert"
        />
      )}
      <TextInput
        id="username"
        name="username"
        value={username}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setUsername(e.target.value.trim())
        }
        labelText={t("username")}
        invalid={usernameError.hasError}
        invalidText={usernameError.errorText}
        placeholder={t("username")}
        onBlur={({ target }) => {
          if (target.value.trim().length < 1) {
            setUsernameError({
              hasError: true,
              errorText: "Username is required",
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
        onKeyDown={(e: React.KeyboardEvent) => {
          if (e.key === "Enter") {
            submit();
          }
        }}
        labelText={t("password")}
        invalid={passwordError.hasError}
        invalidText={passwordError.errorText}
        placeholder={t("password")}
        onBlur={({ target }) => {
          if (target.value.trim().length < 1) {
            setPasswordError({
              hasError: true,
              errorText: "Password is required",
            });
          } else {
            setPasswordError({
              hasError: false,
              errorText: "",
            });
          }
        }}
      />
      <Button onClick={submit}>{t("login")}</Button>
    </LoginFormContainer>
  );
};

function getRedirectUrl(queryString: string) {
  const params = new URLSearchParams(queryString);
  const next = params.get("next");
  if (!next || !/^(\/\w+)+$/.test(next)) {
    return null;
  }
  return next;
}

export const LoginPage: React.FC = () => {
  const { t, Translate } = useTranslate();
  const location = useLocation();
  const license = useLicense();

  const redirectUrl = getRedirectUrl(location.search);
  const onSuccess = useCallback(() => {
    window.location.href = redirectUrl ?? "/identity/users";
  }, [redirectUrl]);
  const hasProductionLicense = license?.isCommercial;

  return (
    <LoginPageContainer>
      <Content>
        <Header>
          <CamundaLogo />
          <h1>{t("identity")}</h1>
        </Header>
        <LoginForm onSuccess={onSuccess} />
        {!hasProductionLicense && (
          <LicenseInfo>
            <Translate i18nKey="licenseInfo">
              Non-Production License. If you would like information on
              production usage, please refer to our{" "}
              <Link
                href="https://legal.camunda.com/#self-managed-non-production-terms"
                target="_blank"
                inline
              >
                terms & conditions page
              </Link>{" "}
              or{" "}
              <Link href="https://camunda.com/contact/" target="_blank" inline>
                contact sales
              </Link>
              .
            </Translate>
          </LicenseInfo>
        )}
      </Content>
      <CopyrightNotice>{getCopyrightNoticeText()}</CopyrightNotice>
    </LoginPageContainer>
  );
};
