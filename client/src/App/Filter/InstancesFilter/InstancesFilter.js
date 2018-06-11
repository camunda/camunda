import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function InstancesFilter({type}) {
  function CheckboxWithLabel({label}) {
    return (
      <div>
        <Styled.Checkbox type="checkbox" />
        <Styled.Label>{label}</Styled.Label>
      </div>
    );
  }
  return (
    <Styled.Filters>
      <CheckboxWithLabel label="Running Instances" />
      <Styled.SubSetFilters>
        <CheckboxWithLabel label="Active" />
        <CheckboxWithLabel label="Incidet" />
      </Styled.SubSetFilters>
    </Styled.Filters>
  );
}

InstancesFilter.propTypes = {
  type: PropTypes.oneOf(['running'])
};
