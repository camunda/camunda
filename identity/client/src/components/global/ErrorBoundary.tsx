/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Component, FC, PropsWithChildren, ReactNode } from "react";
import { CodeSnippet, Link } from "@carbon/react";
import ErrorPage from "src/components/global/ErrorPage";
import useTranslate from "src/utility/localization";

type ErrorBoundaryProps = {
  children?: ReactNode;
};

type ErrorBoundaryState = {
  error: Error | null;
};

const ErrorContent: FC<PropsWithChildren> = ({ children }) => {
  const { Translate } = useTranslate();

  return (
    <ErrorPage title={<Translate>Failmunda - Unknown Error</Translate>}>
      <p>
        <Translate>
          An unknown error has occurred. Please{" "}
          <Link href={document.location.href}>reload the page</Link> or try
          again later.
        </Translate>
      </p>
      <CodeSnippet type="multi">{children}</CodeSnippet>
    </ErrorPage>
  );
};

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  render() {
    const { error } = this.state;
    const { children } = this.props;

    if (error !== null) {
      return <ErrorContent>{error.stack}</ErrorContent>;
    }

    return children;
  }
}

export default ErrorBoundary;
