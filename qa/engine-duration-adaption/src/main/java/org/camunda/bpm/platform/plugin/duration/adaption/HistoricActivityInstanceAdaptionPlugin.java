package org.camunda.bpm.platform.plugin.duration.adaption;

import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.producer.HistoryEventProducer;

/**
 * Use a custom hisotry event producer, which adapts the duration
 * of certain activities.
 */
public class HistoricActivityInstanceAdaptionPlugin extends AbstractProcessEnginePlugin {

  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    HistoryEventProducer historyEventProducer = processEngineConfiguration.getHistoryEventProducer();
    if (historyEventProducer == null) {
      historyEventProducer = new DurationAdaptionHistoryEventProducer();
      processEngineConfiguration.setHistoryEventProducer(historyEventProducer);
    }
  }

}
