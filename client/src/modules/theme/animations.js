import {keyframes} from 'styled-components';

const Animations = {
  Spinner: keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
};
`
};

export default Animations;
