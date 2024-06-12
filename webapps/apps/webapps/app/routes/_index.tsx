import type {MetaFunction} from '@remix-run/node';

export const meta: MetaFunction = () => {
  return [
    {title: 'Camunda Webapps'},
    {name: 'description', content: 'Welcome Camunda!'},
  ];
};

export default function Index() {
  return <></>;
}
