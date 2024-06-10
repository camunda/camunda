import useTranslate from "src/utility/localization";

export default function Groups() {
  const { Translate } = useTranslate();

  return (
    <h1>
      <Translate>Groups</Translate>
    </h1>
  );
}
