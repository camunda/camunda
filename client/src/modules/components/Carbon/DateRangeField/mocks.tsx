/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {dateRangePopoverStore} from 'modules/stores/dateRangePopover';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
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
      <ThemeProvider>
        <Form onSubmit={() => {}} initialValues={initialValues}>
          {() => children}
        </Form>
        <div>Outside element</div>
      </ThemeProvider>
    );
  };

  return Wrapper;
};

export {getWrapper, MockDateRangeField};
