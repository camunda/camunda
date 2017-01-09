package org.camunda.optimize.service;

import org.camunda.optimize.service.es.HeatMapReader;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class HeatMapService {
  //TODO: should be managed by some sort of pooling mechanic
  private TransportClient client;
  private HeatMapReader heatMapReader;

  public HeatMapService() {
    try {
      client = new PreBuiltTransportClient(Settings.EMPTY)
          //TODO: port and host should come from properties
          .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    heatMapReader = new HeatMapReader();
    heatMapReader.setEsclient(client);
  }

  public Map<String, Long> getHeatMap(String key) {
    return heatMapReader.getHeatMap(key);
  }

  public Long activityCorrelation (String process, List<String> activities) {
    return heatMapReader.activityCorrelation(process,activities);
  }
}
