/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode, useEffect, useRef } from "react";
import {
  ButtonSet,
  ComposedModal,
  InlineLoading,
  Modal as CarbonModal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@carbon/react";
import styled from "styled-components";
import useTranslate from "src/utility/localization";

const ModalWrapper = styled.div<{ $overflowVisible: boolean }>`
  ${({ $overflowVisible }) =>
    $overflowVisible
      ? `
  & .cds--modal-content,
  & .cds--modal-container {
    overflow: visible;
    max-height: none;
  }
  &.cds--modal, & .cds--modal {
    overflow: auto;
  }
  & .cds--modal-content {
    margin-bottom: 0;
    padding-bottom: 3rem;
  }
  `
      : ""}
`;

const FooterButtonSet = styled(ButtonSet)`
  width: 100%;
`;

export type ModalProps = {
  open: boolean;
  headline: string;
  onClose: () => void;
  children?: ReactNode;
  danger?: boolean;
  overflowVisible?: boolean;
  confirmLabel: string;
  onSubmit?: () => unknown;
  submitDisabled?: boolean;
  loading?: boolean;
  loadingDescription?: string | null;
  buttons?: ReactNode[];
  size?: "xs" | "sm" | "md" | "lg";
};

const Modal: FC<ModalProps> = ({
  children,
  open,
  onClose,
  headline,
  confirmLabel,
  danger = false,
  overflowVisible = false,
  onSubmit = () => undefined,
  submitDisabled = false,
  loading = false,
  loadingDescription,
  buttons,
  size,
}) => {
  const { t } = useTranslate("components");
  const submitLoading = (
    <InlineLoading description={loadingDescription || t("loading")} />
  );

  const modalRef = useRef<HTMLDivElement>(null);
  const findFocusableCandidate = (
    container: HTMLDivElement,
  ): HTMLInputElement | undefined => {
    return container.querySelector(
      "button:not([disabled])",
    ) as HTMLInputElement;
  };

  useEffect(() => {
    if (
      modalRef.current &&
      !modalRef.current.contains(document.activeElement)
    ) {
      const focusableInput = findFocusableCandidate(modalRef.current);
      focusableInput?.focus();
    }
  }, []);
  const modal =
    buttons === undefined ? (
      <CarbonModal
        size={size}
        modalHeading={headline}
        aria-label={headline}
        open={open}
        primaryButtonText={loading ? submitLoading : confirmLabel}
        primaryButtonDisabled={submitDisabled || loading}
        closeButtonLabel={t("close")}
        secondaryButtonText={t("cancel")}
        onRequestSubmit={onSubmit}
        onRequestClose={onClose}
        onSecondarySubmit={onClose}
        danger={danger}
        ref={modalRef}
      >
        {children}
      </CarbonModal>
    ) : (
      <ComposedModal
        aria-label={headline}
        open={open}
        onClose={onClose}
        ref={modalRef}
      >
        <ModalHeader title={headline} />
        <ModalBody hasForm>{children}</ModalBody>
        <ModalFooter>
          <FooterButtonSet>{buttons}</FooterButtonSet>
        </ModalFooter>
      </ComposedModal>
    );

  return (
    <ModalWrapper $overflowVisible={overflowVisible}>{modal}</ModalWrapper>
  );
};

export const DeleteModal: FC<
  Omit<ModalProps, "confirmLabel" | "buttons" | "size"> &
    Partial<Pick<ModalProps, "confirmLabel">>
> = ({ children, ...modalProps }) => {
  const { t } = useTranslate("components");

  return (
    <Modal confirmLabel={t("delete")} {...modalProps} danger size="sm">
      {children}
    </Modal>
  );
};

export default Modal;
