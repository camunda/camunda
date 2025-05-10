/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Stack} from '@carbon/react';
import {CaretRight, CaretLeft} from '@carbon/react/icons';
import styles from './Pagination.module.scss';
import cn from 'classnames';
import {useTranslation} from 'react-i18next';

type Props = {
  currentPage: number;
  totalPages: number;
  onNextPage: () => void;
  onPreviousPage: () => void;
  className?: string;
};

const Pagination: React.FC<Props> = ({
  currentPage,
  totalPages,
  onNextPage,
  onPreviousPage,
  className,
}) => {
  const {t} = useTranslation();
  const isFirstPage = currentPage === 1;
  const isLastPage = currentPage === totalPages;

  return (
    <Stack
      orientation="horizontal"
      gap={2}
      className={cn(styles.pagination, className)}
    >
      <Button
        onClick={onPreviousPage}
        disabled={isFirstPage}
        size="sm"
        kind="ghost"
        renderIcon={CaretLeft}
        hasIconOnly
        iconDescription={t('processesTabPreviousPageButtonLabel')}
      />
      <Button
        onClick={onNextPage}
        disabled={isLastPage}
        size="sm"
        kind="ghost"
        renderIcon={CaretRight}
        hasIconOnly
        iconDescription={t('processesTabNextPageButtonLabel')}
      />
      <span className={styles.paginationText}>
        {t('processesTabPaginationOfLabel', {
          currentPage: currentPage.toString(),
          totalPages: totalPages.toString(),
        })}
      </span>
    </Stack>
  );
};

export {Pagination};
