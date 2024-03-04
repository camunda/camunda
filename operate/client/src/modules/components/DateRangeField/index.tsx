/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useRef} from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {formatDate, formatISODate, formatTime} from './formatDate';
import {Calendar} from '@carbon/react/icons';
import {DateRangeModal} from './DateRangeModal';
import {IconTextInput} from '../IconInput';

type Props = {
  filterName: string;
  popoverTitle: string;
  label: string;
  fromDateTimeKey: string;
  toDateTimeKey: string;
  isModalOpen: boolean;
  onModalClose: () => void;
  onClick: () => void;
};

const formatInputValue = (fromDateTime?: Date, toDateTime?: Date) => {
  if (fromDateTime === undefined || toDateTime === undefined) {
    return '';
  }
  return `${formatDate(fromDateTime)} ${formatTime(
    fromDateTime,
  )} - ${formatDate(toDateTime)} ${formatTime(toDateTime)}`;
};

const DateRangeField: React.FC<Props> = observer(
  ({
    filterName,
    popoverTitle,
    label,
    fromDateTimeKey,
    toDateTimeKey,
    isModalOpen,
    onModalClose,
    onClick,
  }) => {
    const textFieldRef = useRef<HTMLDivElement>(null);
    const form = useForm();
    const fromDateTime = useField<string>(fromDateTimeKey).input.value;
    const toDateTime = useField<string>(toDateTimeKey).input.value;

    const getInputValue = () => {
      if (isModalOpen) {
        return 'Custom';
      }
      if (fromDateTime !== '' && toDateTime !== '') {
        return formatInputValue(new Date(fromDateTime), new Date(toDateTime));
      }
      return '';
    };

    const handleClick = () => {
      if (!isModalOpen) {
        onClick();
        tracking.track({
          eventName: 'date-range-popover-opened',
          filterName,
        });
      }
    };

    return (
      <>
        <div ref={textFieldRef}>
          <IconTextInput
            Icon={Calendar}
            id={`optional-filter-${filterName}`}
            labelText={label}
            value={getInputValue()}
            title={getInputValue()}
            placeholder="Enter date range"
            size="sm"
            buttonLabel="Open date range modal"
            onIconClick={handleClick}
            onClick={handleClick}
          />
          {[fromDateTimeKey, toDateTimeKey].map((filterKey) => (
            <Field
              name={filterKey}
              key={filterKey}
              component="input"
              type="hidden"
            />
          ))}
        </div>

        {isModalOpen ? (
          <DateRangeModal
            isModalOpen={isModalOpen}
            title={popoverTitle}
            filterName={filterName}
            onCancel={onModalClose}
            onApply={({fromDateTime, toDateTime}) => {
              onModalClose();
              form.change(fromDateTimeKey, formatISODate(fromDateTime));
              form.change(toDateTimeKey, formatISODate(toDateTime));
            }}
            defaultValues={{
              fromDate:
                fromDateTime === '' ? '' : formatDate(new Date(fromDateTime)),
              fromTime:
                fromDateTime === '' ? '' : formatTime(new Date(fromDateTime)),
              toDate: toDateTime === '' ? '' : formatDate(new Date(toDateTime)),
              toTime: toDateTime === '' ? '' : formatTime(new Date(toDateTime)),
            }}
            key={`date-range-modal-${isModalOpen ? 'open' : 'closed'}`}
          />
        ) : null}
      </>
    );
  },
);

export {DateRangeField};
