import {jsx, Text, Select} from 'view-utils';
import {Authenticated} from '../login';
import {StaticLink} from 'router';

export function BComponent({selector}) {
  return <Authenticated routeName="login">
    <Select selector={selector}>
      bbb
      <a>
        <StaticLink name="a" params={{a: 'alina', b: 24}}></StaticLink>
        open a <br />
        <Text property="b" />
      </a>
    </Select>
  </Authenticated>;
}

export function reducer() {
  return {
    b: 'b-cat'
  };
}
