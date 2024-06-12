export function loader() {
  console.log('loader');
  return null;
}

const Component: React.FC = () => {
  console.log('foo');
  return <div>Tasks</div>;
};

export default Component;
