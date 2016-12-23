import {jsx, SetInputFieldValue, OnEvent, dispatchAction, Select, Match, Case} from 'view-utils';
import {performLogin, changePassword, changeUser} from './loginForm.service';

export function LoginForm({selector}) {
  return <Select selector={selector}>
    <form className="login">
      <div>
        <Match>
          <Case predicate={isError}>
            <div class="login__error">
              Incorrect login attempt! Calling cat police force!
            </div>
          </Case>
        </Match>
      </div>
      <OnEvent event="submit" listener={submit} />
      <h1 className="login__title">Login</h1>
      <div className="login__section">
        <span className="login__text">user:</span>
        <input type="text">
          <SetInputFieldValue getValue="user" />
          <OnEvent event="keyup" listener={onUserKeyup} />
        </input>
      </div>
      <div className="login__section">
        <span className="login__text">password:</span>
        <input type="password">
          <SetInputFieldValue getValue="password" />
          <OnEvent event="keyup" listener={onPasswordKeyup} />
        </input>
      </div>
      <div className="login__section" style="flex-direction: row-reverse">
        <button type="submit">Login</button>
      </div>
    </form>
  </Select>;

  function isError({error}) {
    return error;
  }

  function submit({state: {user, password}, event}) {
    event.preventDefault();

    performLogin(user, password);
  }

  function onUserKeyup({node}) {
    changeUser(node.value);
  }

  function onPasswordKeyup({node}) {
    changePassword(node.value);
  }
}
