import { FC, FormEvent } from "react";
import { Form, Stack } from "@carbon/react";
import { spacing04 } from "@carbon/elements";
import styled from "styled-components";
import Modal, { ModalProps } from "./Modal";
import useTranslate from "../../utility/localization";

const HiddenSubmitButton = styled.input`
  display: none;
`;

const FormModal: FC<ModalProps> = ({ children, onSubmit, ...modalProps }) => {
  const formSubmitHandler = (e: FormEvent) => {
    e.preventDefault();
    onSubmit?.();
  };

  return (
    <Modal {...modalProps} onSubmit={onSubmit}>
      <Form onSubmit={formSubmitHandler}>
        <Stack gap={spacing04}>{children}</Stack>
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
