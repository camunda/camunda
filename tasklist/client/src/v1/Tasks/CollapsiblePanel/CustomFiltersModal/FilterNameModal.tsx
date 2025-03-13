/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TextInput, ModalHeader, ModalBody, ModalFooter} from '@carbon/react';
import {Modal} from 'common/components/Modal';
import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';

type Props = {
  isOpen: boolean;
  onApply: (filterName: string) => void;
  onCancel: () => void;
};

const FilterNameModal: React.FC<Props> = ({isOpen, onApply, onCancel}) => {
  const {t} = useTranslation();

  return (
    <Modal
      variant="composed-modal"
      open={isOpen}
      aria-label={t('customFiltersModalSaveAria')}
      preventCloseOnClickOutside
      size="sm"
    >
      {isOpen ? (
        <Form<{filterName: string}>
          onSubmit={(values) => {
            onApply(values.filterName);
          }}
          validate={({filterName}) => {
            const errors: {
              filterName?: string;
            } = {};

            if (!filterName) {
              errors.filterName = t('customFiltersModalNameRequiredError');
            }

            return errors;
          }}
        >
          {({handleSubmit, form}) => (
            <>
              <ModalHeader
                title={t('customFiltersModalTitle')}
                buttonOnClick={onCancel}
              />
              <ModalBody hasForm>
                <form onSubmit={handleSubmit}>
                  <Field name="filterName" required>
                    {({input, meta}) => (
                      <TextInput
                        id="filterName"
                        labelText={t('customFiltersNameModalFilterNameLabel')}
                        placeholder={t('customFiltersModalNamePlaceholder')}
                        required
                        value={input.value}
                        onChange={input.onChange}
                        data-modal-primary-focus
                        invalid={meta.error && meta.touched}
                        invalidText={meta.error}
                      />
                    )}
                  </Field>
                </form>
              </ModalBody>
              {/* @ts-expect-error wrong typedefs on Carbon */}
              <ModalFooter
                primaryButtonText={t('customFiltersModalSaveAndApplyButton')}
                secondaryButtonText={t('customFiltersModalCancelButton')}
                onRequestSubmit={form.submit}
                onRequestClose={onCancel}
              />
            </>
          )}
        </Form>
      ) : null}
    </Modal>
  );
};

export {FilterNameModal};
