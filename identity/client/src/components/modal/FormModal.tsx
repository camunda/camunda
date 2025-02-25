import { FC, FormEvent } from "react";
import { Form, Stack, Loading } from "@carbon/react";
import { spacing06, colors } from "@carbon/elements";
import styled from "styled-components";
import Modal, { ModalProps } from "./Modal";
import useTranslate from "../../utility/localization";

// carbon element z-indexes can only be imported using scss modules
import styles from "./FormModal.module.scss";

const HiddenSubmitButton = styled.input`
  display: none;
`;

const LoadingLabel = styled.div`
  position: fixed;
  color: ${colors.white};
  z-index: ${styles.overlayZIndex + 1};
  display: flex;
  align-items: center;
  justify-content: center;
  block-size: 100%;
  inline-size: 100%;
  inset-inline-start: 0;
`;

const FormModal: FC<ModalProps> = ({ children, onSubmit, ...modalProps }) => {
  const formSubmitHandler = (e: FormEvent) => {
    e.preventDefault();
    onSubmit?.();
  };

  return (
    <Modal {...modalProps} onSubmit={onSubmit}>
      {modalProps.loading && (
        <>
          <Loading />
          <LoadingLabel>{modalProps.loadingDescription}</LoadingLabel>
        </>
      )}
      <Form onSubmit={formSubmitHandler}>
        <Stack gap={spacing06}>{children}</Stack>
        <HiddenSubmitButton type="submit" />
      </Form>
    </Modal>
  );
};

export const AddFormModal: FC<Omit<ModalProps, "confirmLabel" | "buttons">> = ({
  children,
  ...modalProps
}) => {
  const { t } = useTranslate("components");

  return (
    <FormModal {...modalProps} confirmLabel={t("Add")}>
      {children}
    </FormModal>
  );
};

export const EditFormModal: FC<
  Omit<ModalProps, "confirmLabel" | "buttons">
> = ({ children, ...modalProps }) => {
  const { t } = useTranslate("components");

  return (
    <FormModal {...modalProps} confirmLabel={t("Edit")}>
      {children}
    </FormModal>
  );
};

export default FormModal;
