/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useCallback, useState } from "react";
import Page from "src/components/layout/Page.tsx";
import useTranslate from "src/utility/localization";
import { Button, Link, PasswordInput, TextInput } from "@carbon/react";
import "./LoginPage.scss";
import { login } from "src/utility/auth";
import { useLocation } from "react-router-dom";
import { getCopyrightNoticeText } from "src/utility/copyright.ts";
import camundaLogo from "src/assets/images/camunda.svg";
import { useLicense } from "src/utility/license.ts";

interface LoginFormProps {
  onSuccess: () => void;
}

const LoginForm: React.FC<LoginFormProps> = ({ onSuccess }) => {
  const { t } = useTranslate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const submit = useCallback(() => {
    login(username, password).then((success) => {
      if (success) {
        onSuccess();
      }
    });
  }, [onSuccess, username, password]);

  return (
    <div className="LoginForm">
      <TextInput
        id="username"
        name="username"
        value={username}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setUsername(e.target.value)
        }
        labelText={t("username")}
        invalid={false}
        invalidText={undefined}
        placeholder={t("enterUsername")}
      />
      <PasswordInput
        id="password"
        name="password"
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setPassword(e.target.value)
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
        invalid={false}
        invalidText={undefined}
        placeholder={t("loginPasswordFieldPlaceholder")}
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
  const redirectUrl = getRedirectUrl(location.search);
  const onSuccess = useCallback(() => {
    window.location.href = redirectUrl ?? "/identity/users";
  }, [redirectUrl]);
  const hasProductionLicense = license?.isCommercial;

  return (
    <Page className="LoginPage">
      <div className="content">
        <div className="header">
          <img src={camundaLogo} alt="Camunda" />
          <h1>{t("identity")}</h1>
        </div>
        <LoginForm onSuccess={onSuccess} />
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
