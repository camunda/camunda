import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

function Content(props) {
  return <Styled.Content>{props.children}</Styled.Content>;
}

Content.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]).isRequired
};

export default Content;
