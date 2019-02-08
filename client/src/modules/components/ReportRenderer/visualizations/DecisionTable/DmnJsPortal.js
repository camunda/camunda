import ReactDOM from 'react-dom';

export default function DmnJsPortal({children, renderIn}) {
  if (renderIn) {
    return ReactDOM.createPortal(children, renderIn);
  }
  return null;
}
