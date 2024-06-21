import { FC, useEffect, useState } from "react";
import useDebounce from "react-debounced";
import { Loading } from "@carbon/react";

type LateLoadingProps = {
  timeout?: number;
};

const LateLoading: FC<LateLoadingProps> = ({ timeout = 300 }) => {
  const debounce = useDebounce(timeout);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    debounce(() => setVisible(true));
  }, [debounce]);

  if (visible) {
    return <Loading />;
  }

  return null;
};

export default LateLoading;
