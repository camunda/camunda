/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';
import {Edit, View, ViewOff} from '@carbon/icons-react';

import {t} from 'translation';

import {DurationHeatmapModal} from './DurationHeatmap';

import './TargetValueComparison.scss';

export default function TargetValueComparison({report, onChange}) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const {active} = getConfig();

  function getConfig() {
    return report.data.configuration.heatmapTargetValue;
  }

  function hasValues() {
    const {values} = getConfig();
    return values && Object.keys(values).length > 0;
  }

  function toggleMode() {
    if (getConfig().active) {
      setActive(false);
    } else if (!hasValues()) {
      openModal();
    } else {
      setActive(true);
    }
  }

  function setActive(active) {
    onChange({
      configuration: {
        heatmapTargetValue: {
          active: {$set: active},
        },
      },
    });
  }

  async function openModal() {
    setIsModalOpen(true);
  }

  function closeModal() {
    setIsModalOpen(false);
  }

  function confirmModal(values) {
    onChange({
      configuration: {
        heatmapTargetValue: {
          $set: {
            active: Object.keys(values).length > 0,
            values,
          },
        },
      },
    });
    closeModal();
  }

  function isResultAvailable() {
    return typeof report.result !== 'undefined';
  }

  function getButtonIcon() {
    if (!hasValues()) {
      return undefined;
    }
    if (active) {
      return View;
    }
    return ViewOff;
  }

  return (
    <div className="TargetValueComparison">
      <Button
        size="sm"
        kind={hasValues() ? 'tertiary' : 'ghost'}
        className="toggleButton"
        onClick={toggleMode}
        renderIcon={getButtonIcon()}
      >
        {hasValues() ? t('report.config.goal.markTargets') : t('common.add')}
      </Button>
      <Button
        size="sm"
        kind="ghost"
        className="targetEditButton"
        onClick={openModal}
        renderIcon={Edit}
        hasIconOnly
        iconDescription={t('common.edit')}
      />
      {isResultAvailable() && (
        <DurationHeatmapModal
          open={isModalOpen}
          onClose={closeModal}
          onConfirm={confirmModal}
          report={report}
        />
      )}
    </div>
  );
}
