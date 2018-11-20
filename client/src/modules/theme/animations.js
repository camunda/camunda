import {keyframes} from 'styled-components';

const Animations = {
  Spinner: keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
`,
  Selection: keyframes`
  0% {
    opacity: 0.3;
  }
  100% {
    opacity: 1;
  }
`
};

export default Animations;
