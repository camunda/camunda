/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {dateRangePopoverStore} from 'modules/stores/dateRangePopover';
import {useEffect, useState} from 'react';
import {Form} from 'react-final-form';
import {DateRangeField} from '.';

const MockDateRangeField: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  return (
    <DateRangeField
      onModalClose={() => {
        setIsModalOpen(false);
      }}
      onClick={() => {
        setIsModalOpen(true);
      }}
      isModalOpen={isModalOpen}
      popoverTitle="Filter instances by start date"
      label="Start Date Range"
      filterName="startDateRange"
      fromDateTimeKey="startDateAfter"
      toDateTimeKey="startDateBefore"
    />
  );
};

const getWrapper = (initialValues?: {[key: string]: string}) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        dateRangePopoverStore.reset();
      };
    }, []);

    return (
      <>
        <Form onSubmit={() => {}} initialValues={initialValues}>
          {() => children}
        </Form>
        <div>Outside element</div>
      </>
    );
  };

  return Wrapper;
};

export {getWrapper, MockDateRangeField};
