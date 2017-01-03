import {jsx, Select, setInputValue, OnEvent} from 'view-utils';

const dateRegExp = /^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})$/;

export function DateFilter({selector, onDateChanged, defaultDate = new Date()}) {
  return <Select selector={selector}>
    <input type="text">
      <SetDateValue defaultDate={defaultDate} />
      <OnEvent event="change" listener={onFieldChanged}/>
    </input>
  </Select>;

  function onFieldChanged({node: input}) {
    const date = parseDate(input.value);

    if (date) {
      onDateChanged(date);
    }
  }

  function parseDate(dateStr) {
    const matched = dateStr.match(dateRegExp);

    if (matched) {
      const [year, month, day] = matched
        .slice(1)
        .map(n => +n);

      if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
        return new Date(year, month - 1, day);
      }
    }
  }
}

function SetDateValue({defaultDate}) {
  let lastValue;

  return node => {
    return value => {
      if (lastValue !== value) {
        let date = value;

        if (!value instanceof Date) {
          date = defaultDate;
        }

        setInputValue(node, `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`);
        lastValue = date;
      }
    };
  }
}
