import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function IncidentsBar({id, count, onClick, isArrowFlipped}) {
  const isOnlyOne = count === 1;
  const errorMessage = `There ${isOnlyOne ? 'is' : 'are'} ${count} incident${
    isOnlyOne ? '' : 's'
  } in Instance ${id}. `;

  return (
    <Styled.IncidentsBar onClick={onClick}>
      <Styled.Arrow isFlipped={isArrowFlipped} />
      {errorMessage}
    </Styled.IncidentsBar>
  );
}

IncidentsBar.propTypes = {
  id: PropTypes.string.isRequired,
  count: PropTypes.number.isRequired,
  onClick: PropTypes.func,
  isArrowFlipped: PropTypes.bool
};
