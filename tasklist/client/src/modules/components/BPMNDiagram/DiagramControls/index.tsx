import {IconButton} from '@carbon/react';
import {Add, CenterCircle, Subtract} from '@carbon/react/icons';
import styles from './styles.module.scss';
import {useTranslation} from 'react-i18next';

type Props = {
  handleZoomReset: () => void;
  handleZoomIn: () => void;
  handleZoomOut: () => void;
};

const DiagramControls: React.FC<Props> = ({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
}) => {

  const {t} = useTranslation();

  return (
    <div className={styles.container}>
      <IconButton
        className={styles.zoomReset}
        size="sm"
        kind="tertiary"
        align="left"
        label={t('resetDiagramZoom')}
        aria-label={t('resetDiagramZoom')}
        onClick={handleZoomReset}
      >
        <CenterCircle />
      </IconButton>
      <IconButton
        className={styles.zoomIn}
        size="sm"
        kind="tertiary"
        align="left"
        label={t('zoomInDiagram')}
        aria-label={t('zoomInDiagram')}
        onClick={handleZoomIn}
      >
        <Add />
      </IconButton>
      <IconButton
        className={styles.zoomOut}
        size="sm"
        kind="tertiary"
        align="left"
        label={t('zoomOutDiagram')}
        aria-label={t('zoomOutDiagram')}
        onClick={handleZoomOut}
      >
        <Subtract />
      </IconButton>
    </div>
  );
};

export {DiagramControls};