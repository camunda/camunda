/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useState } from "react";
import { Alert } from "@camunda/design-system";
import {
  ApiError,
  ErrorResponse,
  isDetailedError,
} from "src/utility/api/request";
import Modal, { ModalProps } from "./Modal";

type FormModalProps = {
  error?: ApiError | Error | ErrorResponse<"detailed"> | null;
} & ModalProps;

const FormModal: React.FC<FormModalProps> = ({
  children,
  onSubmit,
  error,
  ...modalProps
}) => {
  const [showError, setShowError] = useState(true);

  // The footer button and the form's implicit submit both land here, so a
  // dismissed error reappears whichever way the retry was triggered. The
  // in-flight guard covers the keyboard path: the footer button blocks its own
  // repeat clicks while `loading`, pressing Enter in a field does not.
  const submit = () => {
    if (modalProps.loading) return;
    setShowError(true);
    onSubmit?.();
  };

  const formSubmitHandler = (e: React.SubmitEvent) => {
    e.preventDefault();
    submit();
  };

  const apiErrorBody = (() => {
    if (!error) return null;
    if (error instanceof ApiError) {
      return error.body && isDetailedError(error.body) ? error.body : null;
    }
    if (error instanceof Error) return null;
    return isDetailedError(error) ? error : null;
  })();

  return (
    <Modal {...modalProps} onSubmit={submit}>
      <form onSubmit={formSubmitHandler}>
        <div className="grid gap-6">
          {children}
          {apiErrorBody && showError && (
            <Alert
              variant="destructive"
              title={apiErrorBody.title}
              description={apiErrorBody.detail}
              dismissible
              onDismiss={() => setShowError(false)}
            />
          )}
        </div>
        <input type="submit" className="hidden" />
      </form>
    </Modal>
  );
};

export default FormModal;
