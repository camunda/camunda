/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useCallback, useState } from "react";
import { useLocation } from "react-router-dom";
import { Button, Link, PasswordInput, TextInput } from "@carbon/react";
import { useNotifications } from "src/components/notifications";
import Page from "src/components/layout/Page.tsx";
import useTranslate from "src/utility/localization";
import { login } from "src/utility/auth";
import { getCopyrightNoticeText } from "src/utility/copyright.ts";
import CamundaLogo from "src/assets/images/camunda.svg";
import { useLicense } from "src/utility/license.ts";
import "src/pages/login/LoginPage.scss";

interface LoginFormProps {
  onSuccess: () => void;
  onFail: () => void;
}

const LoginForm: React.FC<LoginFormProps> = ({ onSuccess, onFail }) => {
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

  const submit = useCallback(() => {
    if (!username) {
      setUsernameError({
        hasError: true,
        errorText: "Username field is required",
      });
    }

    if (!password) {
      setPasswordError({
        hasError: true,
        errorText: "Password field is required",
      });
    }

    if (username && password) {
      login(username, password).then((success) => {
        if (success) {
          onSuccess();
        } else {
          onFail();
        }
      });
    }
  }, [onSuccess, onFail, username, password]);

  return (
    <div className="LoginForm">
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
              errorText: "Username field is required",
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
              errorText: "Password field is required",
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
    </div>
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
  const { enqueueNotification } = useNotifications();

  const redirectUrl = getRedirectUrl(location.search);
  const onSuccess = useCallback(() => {
    window.location.href = redirectUrl ?? "/identity/users";
  }, [redirectUrl]);
  const onFail = useCallback(() => {
    enqueueNotification({
      kind: "error",
      title: "Login failed",
      subtitle: "That username and password combination is incorrect.",
    });
  }, [enqueueNotification]);
  const hasProductionLicense = license?.isCommercial;

  return (
    <Page className="LoginPage">
      <div className="content">
        <div className="header">
          <CamundaLogo />
          <h1>{t("identity")}</h1>
        </div>
        <LoginForm onSuccess={onSuccess} onFail={onFail} />
        {!hasProductionLicense && (
          <div className="license-info">
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
          </div>
        )}
      </div>
      <div className="copyright-notice">{getCopyrightNoticeText()}</div>
    </Page>
  );
};
