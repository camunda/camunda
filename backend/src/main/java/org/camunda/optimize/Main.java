package org.camunda.optimize;

import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;

/**
 * @author Askar Akhmerov
 */
public class Main {

  private static EmbeddedCamundaOptimize jettyCamundaOptimize;

  public static void main(String[] args) throws Exception {
    jettyCamundaOptimize = new EmbeddedCamundaOptimize();
    try {
      jettyCamundaOptimize.startOptimize();
      jettyCamundaOptimize.startImportSchedulers();
      jettyCamundaOptimize.join();
    } finally {
      jettyCamundaOptimize.destroyOptimize();
    }
  }


}
