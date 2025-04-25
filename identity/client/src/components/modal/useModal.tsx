/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ComponentType, ReactNode, useCallback, useState } from "react";
import useDebounce from "react-debounced";
import { modalFadeDurationMs } from "src/utility/style";

export type UseModalProps = {
  open: boolean;
  onSuccess: () => void;
  onClose: () => void;
};

const useModalState = () => {
  const debounce = useDebounce(modalFadeDurationMs);
  const [isOpen, setOpen] = useState(false);
  const [isVisible, setVisible] = useState(false);

  const openModal = useCallback(() => {
    setOpen(true);
    setVisible(true);
    debounce(() => null);
  }, [debounce]);

  const closeModal = useCallback(() => {
    setOpen(false);
    debounce(() => setVisible(false));
  }, [debounce]);

  return { isOpen, isVisible, openModal, closeModal };
};

const useModal = <P,>(
  ...params:
    | [ComponentType<UseModalProps>, () => unknown]
    | [ComponentType<UseModalProps & P>, () => unknown, P]
): [() => void, ReactNode] => {
  const [ModalComponent, onSuccess, props = {}] = params;
  const { isOpen, isVisible, openModal, closeModal } = useModalState();

  const modal = isVisible ? (
    <ModalComponent
      open={isOpen}
      onClose={closeModal}
      onSuccess={() => {
        closeModal();
        onSuccess();
      }}
      {...(props as P)}
    />
  ) : null;

  return [openModal, modal];
};

export type UseEntityModalProps<E> = UseModalProps & {
  entity: E;
};

export type UseEntityModalCustomProps<E, P> = P & UseEntityModalProps<E>;

type UseEntityModalParams<E> = [
  Component: ComponentType<UseEntityModalProps<E>>,
  onSuccess: () => void,
];

type UseEntityModalWithPropsParams<E, P> = [
  Component: ComponentType<UseEntityModalCustomProps<E, P>>,
  onSuccess: () => void,
  customProps: P,
];

export const useEntityModal = <E, P extends { [key: string]: unknown }>(
  ...params: UseEntityModalParams<E> | UseEntityModalWithPropsParams<E, P>
): [(selectEntity: E) => void, ReactNode] => {
  const [Component, onSuccess, customProps] = params;

  const { isOpen, isVisible, openModal, closeModal } = useModalState();
  const [entity, setEntity] = useState<E | null>(null);

  const showModal = useCallback(
    (selectEntity: E) => {
      setEntity(selectEntity);
      openModal();
    },
    [openModal],
  );

  const modal =
    isVisible && entity ? (
      <Component
        open={isOpen}
        entity={entity}
        onClose={closeModal}
        onSuccess={() => {
          closeModal();
          onSuccess();
        }}
        {...(customProps as P)}
      />
    ) : null;

  return [showModal, modal];
};

export default useModal;
