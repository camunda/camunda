import useTranslate from "src/utility/localization";
import { useState } from "react";

export default function Users() {
  const { Translate } = useTranslate();
  const [error, setError] = useState(false);

  if (error) {
    throw new Error("this is a test");
  }

  return (
    <>
      <h1>
        <Translate>Users</Translate>
      </h1>
      <button onClick={() => setError(true)}>Click me for an error</button>
    </>
  );
}
