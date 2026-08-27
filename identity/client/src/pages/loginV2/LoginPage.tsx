/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useCallback, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { Button, cn, Heading, Text } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { disableSession, isLoggedIn, login } from "src/utility/auth";
import { getCopyrightNoticeText } from "src/utility/copyright.ts";
import CamundaLogo from "src/assets/images/camunda.svg";
import { useLicense } from "src/utility/license.ts";
import TextField from "src/components/formV2/TextField";
import { ErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import Page from "src/components/layoutV2/Page";

const textLinkClassName =
  "text-info-action-default underline-offset-2 hover:underline focus-visible:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

interface LoginFormProps {
  onSuccess: () => void;
}

const LoginForm: React.FC<LoginFormProps> = ({ onSuccess }) => {
  const { t } = useTranslate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [usernameError, setUsernameError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [submitError, setSubmitError] = useState("");

  const validateUsername = useCallback((value: string) => {
    const isValid = value.trim().length > 0;
    setUsernameError(isValid ? "" : "Username is required");
    return isValid;
  }, []);

  const validatePassword = useCallback((value: string) => {
    const isValid = value.trim().length > 0;
    setPasswordError(isValid ? "" : "Password is required");
    return isValid;
  }, []);

  const submit = useCallback(
    (event: React.SubmitEvent) => {
      event.preventDefault();
      const isUsernameValid = validateUsername(username);
      const isPasswordValid = validatePassword(password);

      if (isUsernameValid && isPasswordValid) {
        void login(username, password).then(({ success, message }) => {
          if (success) {
            onSuccess();
          } else {
            setSubmitError(message);
          }
        });
      }
    },
    [onSuccess, username, password, validateUsername, validatePassword],
  );

  return (
    <form
      onSubmit={submit}
      className={cn(
        "flex w-full flex-col gap-8 pb-8",
        submitError ? "pt-0" : "pt-8",
      )}
    >
      {submitError && <ErrorInlineNotification title={submitError} />}
      <TextField
        label={t("username")}
        value={username}
        onChange={(value) => setUsername(value.trim())}
        onBlur={validateUsername}
        errors={usernameError}
        placeholder={t("username")}
        name="username"
        autoComplete="username"
      />
      <TextField
        type="password"
        label={t("password")}
        value={password}
        onChange={(value) => setPassword(value.trim())}
        onBlur={validatePassword}
        errors={passwordError}
        placeholder={t("password")}
        name="password"
        autoComplete="current-password"
      />
      <Button type="submit">{t("login")}</Button>
    </form>
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

interface LoginPageProps {
  defaultRedirectUrl: string;
}

export const LoginPage: React.FC<LoginPageProps> = ({ defaultRedirectUrl }) => {
  const { t, Translate } = useTranslate();
  const location = useLocation();
  const license = useLicense();

  useEffect(() => {
    if (isLoggedIn()) {
      disableSession();
    }
  });

  const redirectUrl = getRedirectUrl(location.search);
  const onSuccess = useCallback(() => {
    window.location.href = redirectUrl ?? defaultRedirectUrl;
  }, [redirectUrl, defaultRedirectUrl]);
  const hasProductionLicense = license?.isCommercial;

  return (
    <Page className="min-h-screen">
      <div className="mx-auto flex w-100 flex-col">
        <div className="m-8 flex flex-col items-center gap-2">
          <CamundaLogo />
          <Heading as="h1" variant="heading-xl">
            {t("admin")}
          </Heading>
        </div>
        <LoginForm onSuccess={onSuccess} />
        {!hasProductionLicense && (
          <Text as="p" variant="helper" className="w-full text-center">
            <Translate i18nKey="licenseInfo">
              Non-Production License. If you would like information on
              production usage, please refer to our{" "}
              <a
                href="https://legal.camunda.com/#self-managed-non-production-terms"
                target="_blank"
                rel="noreferrer noopener"
                className={textLinkClassName}
              >
                terms & conditions page
              </a>{" "}
              or{" "}
              <a
                href="https://camunda.com/contact/"
                target="_blank"
                rel="noreferrer noopener"
                className={textLinkClassName}
              >
                contact sales
              </a>
              .
            </Translate>
          </Text>
        )}
      </div>
      <Text
        as="p"
        variant="helper"
        className="absolute inset-x-0 bottom-0 p-4 text-center"
      >
        {getCopyrightNoticeText()}
      </Text>
    </Page>
  );
};
