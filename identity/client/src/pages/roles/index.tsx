import useTranslate from "src/utility/localization";

export default function Roles() {
  const { Translate } = useTranslate();

  return (
    <h1>
      <Translate>Roles</Translate>
    </h1>
  );
}
